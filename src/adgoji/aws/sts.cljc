(ns adgoji.aws.sts
  (:require
   [adgoji.aws.util :refer [getx]]
   [adgoji.cognitect.aws.util :as aws.util]
   [adgoji.cognitect.aws.signers :as aws.signers]
   [clojure.data.xml :as xml]
   #?(:cljs [cljs-time.core])

   #?(:cljs [goog.string :as gstring :refer [format]])
   #?(:cljs [goog.string.format])
   ))

(defn now []
  #?(:clj (java.util.Date.)
     :cljs (cljs-time.core/now)))

#_(defn assume-role-request [& _]
       {})

(defn assume-role-request [creds {:keys [role-arn
                                         session-name
                                         region] :as sts}]
  (let [region (getx sts :region)
        auth-info {:access-key-id (getx creds :aws/access-key-id)
                   :secret-access-key (getx creds :aws/secret-access-key)
                   :region region
                   :service "sts"
                   :session-token (getx creds :aws/session-token)}
        role-arn (getx sts :role-arn)
        session-name (getx sts :session-name)
        x-amz-date (aws.util/format-date aws.util/x-amz-date-format (now))
        hostname (str "STS." region ".amazonaws.com")
        session-token (:session-token auth-info)
        req {:request-method :post,
             :scheme :https,
             :server-port 443,
             :uri "/",
             :server-name hostname
             :hostname hostname
             :headers (cond-> {"content-type" "application/x-www-form-urlencoded; charset=utf-8"
                               "host" hostname
                               "x-amz-date" x-amz-date}
                        session-token
                        (assoc "x-amz-security-token", session-token)),

             :body (str "Action=AssumeRole&Version=2011-06-15&RoleSessionName=" (aws.signers/uri-encode session-name) "&RoleArn=" (aws.signers/uri-encode role-arn))}

        ;_ (prn "Req" req "auth-info" auth-info)
        authorization (format "AWS4-HMAC-SHA256 Credential=%s/%s, SignedHeaders=%s, Signature=%s"
                              (getx auth-info :access-key-id)
                              (aws.signers/credential-scope auth-info req)
                              (aws.signers/signed-headers req)
                              (aws.signers/signature auth-info req))]
    (assoc-in req [:headers "authorization"] authorization)))

(defn assume-xml-response->credentials [xml-response]
  (let [parsed (xml/parse-str xml-response)
        children (-> parsed
                     :content
                     second
                     :content)]
    (if-let [creds (not-empty
                    (into {} (comp (keep :content)
                                   cat
                                   (filter map?)
                                   (map
                                    (fn [m]
                                      [(name (:tag m)) (first (:content m))])))
                          children))]
      creds
      {:error parsed})))

(defn assume-role-response [response]
  (assume-xml-response->credentials (:body response)))
