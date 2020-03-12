(ns adgoji.aws.creds
  (:require [clojure.java.shell]
            clojure.edn
            clojure.set
            clojure.java.io
            clojure.string
            [adgoji.cognitect.aws.config :as config]))

(defn run-credential-process-cmd [cmd]
  ;; Use edn reader to split cmd into parts
  (let [cmd (remove #{"'"} (map str (clojure.edn/read-string (str "[" cmd "]"))))
        {:keys [exit out err] :as res}
        (apply clojure.java.shell/sh cmd)
        ;; REVIEW this doesn't help much. Needs some other debugging mechanism
        #_(shell-command cmd {:timeout 2000
                              :to-string? false})]
    (if (zero? exit)
      out
      (throw (ex-info (str "Non-zero exit: " (pr-str err)) {})))))


(defn parse-credentials-json [json]
  ;; The credential json has a simple structure and we only need string values
  ;; No need to include an extra dependency on a json parser
  (let [parts (clojure.string/split json #"[:\}\{,]")
        pairs (map list (cons nil parts) parts)
        strip-qoutes (fn [v] (second (re-find #"\"(.+)\"" v)))]
    (reduce (fn [acc [k v]]
              (case k
                "\"SessionToken\""    (assoc acc "SessionToken"    (strip-qoutes v))
                "\"AccessKeyId\""     (assoc acc "AccessKeyId"     (strip-qoutes v))
                "\"SecretAccessKey\"" (assoc acc "SecretAccessKey" (strip-qoutes v))
                acc))

            {} pairs)))

(defn get-credentials-via-cmd [cmd]
  (let [json (run-credential-process-cmd cmd)]
    (parse-credentials-json json)))

(defn get-profile-creds [profile]
  (let [profiles (config/parse (clojure.java.io/file (str (System/getenv "HOME") "/.aws/credentials")))]
    (if-let [conf (get profiles profile)]
      (if-let [creds-cmd (get conf "credential_process")]
        (get-credentials-via-cmd creds-cmd)
        (throw (ex-info (str "Profile conf compatible" (pr-str conf)) {})))
      (throw (ex-info (str  "Profile " (pr-str profile) " not found") {})))))


(defn format-aws-creds [creds mapping]
  (-> creds
      (clojure.set/rename-keys mapping)
      (select-keys (vals mapping))))

(defn creds->aws-api-creds [creds]
  (format-aws-creds creds {"AccessKeyId" :aws/access-key-id
                           "SecretAccessKey" :aws/secret-access-key
                           "SessionToken"    :aws/session-token}))

(defn creds->simple-aws-api-creds [creds]
  (format-aws-creds creds {"AccessKeyId" :access-key-id
                           "SecretAccessKey" :secret-access-key
                           "SessionToken"    :session-token}))
