(ns adgoji.aws.sts
  (:require
   [adgoji.aws.util :refer [getx]]
   [adgoji.aws.requests :as aws-requests]
   [clojure.data.xml :as xml]))

(def assume-role-template
  {:auth-info {:region "us-east-1", :service "sts", :hostname "sts.amazonaws.com"},
   :request {:request-method :post, :scheme :https, :server-port 443, :uri "/",
             :headers {"content-type" "application/x-www-form-urlencoded; charset=utf-8"},
             :body "Action=AssumeRole&Version=2010-05-15&=%7B%3ARoleSessionName%20%3Aargs%2Frole-session-name%2C%20%3ARoleArn%20%3Aargs%2Frole-arn%7D",
             :hostname "sts.amazonaws.com"}})

(defn assume-role-request [creds {:keys [role-arn session-name region]}]
  (aws-requests/request-template->request-map creds
                                              assume-role-template
                                              {:region region
                                               :args/role-arn role-arn
                                               :args/role-session-name session-name}))


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
