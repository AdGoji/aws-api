(ns adgoji.aws.requests
  (:require
   [adgoji.aws.util :refer [getx]]
   [adgoji.aws.creds :as creds]
   [adgoji.cognitect.aws.util :as aws.util]
   [adgoji.cognitect.aws.signers :as aws.signers]
   [clojure.data.xml :as xml]
   [clojure.set]
   #?(:cljs [cljs-time.core])

   #?(:cljs [goog.string :as gstring :refer [format]])
   #?(:cljs [goog.string.format])
   ))

(defn now []
  #?(:clj (java.util.Date.)
     :cljs (cljs-time.core/now)))

(defn with-auth-headers [auth-info {:keys [hostname headers] :as request}]
  (let [session-token (getx auth-info :session-token)
        x-amz-date (aws.util/format-date aws.util/x-amz-date-format (now))
        request (assoc request :headers (cond-> (merge {"content-type" "application/x-www-form-urlencoded; charset=utf-8"
                                                        "host" hostname
                                                        "x-amz-date" x-amz-date}
                                                       headers)
                                          session-token
                                          (assoc "x-amz-security-token", session-token)))
        authorization (format "AWS4-HMAC-SHA256 Credential=%s/%s, SignedHeaders=%s, Signature=%s"
                              (getx auth-info :access-key-id)
                              (aws.signers/credential-scope auth-info request)
                              (aws.signers/signed-headers request)
                              (aws.signers/signature auth-info request))]
    (assoc-in request [:headers "authorization"] authorization)))


(defn with-macro-replacements [template {:keys [region] :as replacements}]
  (let [template
        (if region
          (let [template-region (get-in template [:auth-info :region])
                region (name region)
                hostname (clojure.string/replace (get-in template [:request :hostname]) template-region region)]
            (-> template
                (assoc-in [:auth-info :region] region)
                (assoc-in [:auth-info :hostname] hostname)
                (assoc-in [:request :hostname] hostname)))
          template)]
    (update-in template [:request :body]
            (fn [body]
              (reduce-kv (fn [acc k v]
                           (let [acc0 (clojure.string/replace acc (aws.signers/uri-encode (str k)) (aws.signers/uri-encode v))]
                             (when (= acc acc0)
                               (throw (ex-info (str "No match found for " k " in template")  {:template template})))
                             acc0))
                         body
                         (dissoc replacements :region))))))

(defn request-template->request-map [creds template template-replacements]
  (let [{:keys [request auth-info]} (with-macro-replacements template template-replacements)]
    (with-auth-headers
      (merge auth-info (creds/creds->simple-aws-api-creds creds))
      request)))
