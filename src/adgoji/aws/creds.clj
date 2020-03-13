(ns adgoji.aws.creds
  (:require [clojure.java.shell]
            clojure.edn
            clojure.set
            clojure.java.io
            clojure.string
            [cheshire.core :as cheshire]
            [adgoji.cognitect.aws.config :as config]))

(defn run-credential-process-cmd [cmd]
  (let [{:keys [exit out err] :as res}
        (clojure.java.shell/sh "sh" "-c" cmd)]
    (if (zero? exit)
      out
      (throw (ex-info (str "Non-zero exit: " (pr-str err)) {})))))

(defn get-credentials-via-cmd [cmd]
  (let [json (run-credential-process-cmd cmd)]
    (cheshire/parse-string json)))

(defn get-profile [profile]
  (let [profiles (config/parse (clojure.java.io/file (str (System/getenv "HOME") "/.aws/credentials")))]
    (get profiles profile)))

(defn get-profile-creds [profile]
  (if-let [conf (get-profile profile)]
    (if-let [creds-cmd (get conf "credential_process")]
      (get-credentials-via-cmd creds-cmd)
      (throw (ex-info (str "Profile conf compatible" (pr-str conf)) {})))
    (throw (ex-info (str  "Profile " (pr-str profile) " not found") {}))))

(defn format-aws-creds [creds mapping]
  (-> (clojure.walk/keywordize-keys creds)
      (clojure.set/rename-keys mapping)))

(defn creds->aws-api-creds [creds]
  (format-aws-creds creds {:AccessKeyId :aws/access-key-id
                           :SecretAccessKey :aws/secret-access-key
                           :SessionToken    :aws/session-token}))

(defn creds->simple-aws-api-creds [creds]
  (format-aws-creds creds {:AccessKeyId :access-key-id
                           :SecretAccessKey :secret-access-key
                           :SessionToken    :session-token}))
