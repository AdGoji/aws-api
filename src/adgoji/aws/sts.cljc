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

(defn format-aws-creds [creds mapping]
  (-> creds
      (clojure.set/rename-keys mapping)
      (select-keys (vals mapping))))

(defn creds->simple-aws-api-creds [creds]
  (format-aws-creds creds {"AccessKeyId" :access-key-id
                           "SecretAccessKey" :secret-access-key
                           "SessionToken"    :session-token}))

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
        (let [template-region (get-in template [:auth-info :region])
              region (name region)
              hostname (clojure.string/replace (get-in template [:request :hostname]) template-region region)]
          (-> template
              (assoc-in [:auth-info :region] region)
              (assoc-in [:auth-info :hostname] hostname)
              (assoc-in [:request :hostname] hostname)))]
    (update-in template [:request :body]
            (fn [body]
              (reduce-kv (fn [acc k v]
                           (clojure.string/replace acc (aws.signers/uri-encode (str k)) (aws.signers/uri-encode v)))
                         body
                         (dissoc replacements :region))))))

(defn request-template->request-map [creds template template-replacements]
  (let [{:keys [request auth-info]} (with-macro-replacements template template-replacements)]
    (with-auth-headers
      (merge auth-info (creds->simple-aws-api-creds creds))
      request)))


(comment
  ;; Generate a request template via cognitect/aws-api library:

  (require '[cognitect.aws.client]
           '[cognitect.aws.endpoint])

  (defn endpoint [service region]
    (cognitect.aws.endpoint/resolve service (keyword region)))

  (endpoint :sts :us-east-1)

  ;; Copy request-template in your code
  (defn request-template [client service region op]
    (let [{:keys [hostname]} (endpoint service region)
          service-map (:service (cognitect.aws.client/-get-info client))]
      {:auth-info {:region (name region)
                   :service (name service)
                   :hostname hostname}
       :request  (-> (cognitect.aws.client/build-http-request service-map op)
                     (update :headers dissoc "x-amz-date")
                     (assoc :hostname hostname))})))

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
