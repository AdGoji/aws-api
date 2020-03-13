(ns examples
  (:require [babashka.curl :as curl]
            [clojure.java.shell]
            [adgoji.aws.requests :as aws-requests]
            [adgoji.aws.creds]
            [cognitect.aws.client]
            [cognitect.aws.endpoint]
            [cognitect.aws.credentials]
            [cognitect.aws.client.api :as aws]))

(defn endpoint [service region]
  (cognitect.aws.endpoint/resolve service (keyword region)))

  ;; Copy request-template in your code
(defn request-template [client service region op]
  (let [{:keys [hostname]} (endpoint service region)
        service-map (:service (cognitect.aws.client/-get-info client))]
    {:auth-info {:region (name region)
                 :service (name service)
                 :hostname hostname}
     :request  (-> (cognitect.aws.client/build-http-request service-map op)
                   (update :headers dissoc "x-amz-date")
                   (assoc :hostname hostname))}))

(defn request->babashka-curl [request]
  (-> request
      (clojure.set/rename-keys {:request-method :method})
      (assoc :url (str (name (:scheme request)) "://" (:hostname request) (some->> (:server-port request) (str ":")) (:uri request)))))

(defn http-request [request]
  (apply clojure.java.shell/sh (curl/curl-command (request->babashka-curl request))))

;; Step 1 get credentials
;; Step 2 test credentials with cognitect client or Amazonica to make sure you are able to call the API
;; Step 3 call API via Cognitect client (you might need to include the right libraries (see https://github.com/cognitect-labs/aws-api/blob/master/latest-releases.edn)

(def creds (adgoji.aws.creds/get-profile-creds "production"))


(def client (aws/client {:api :sts
                         :credentials-provider
                         (reify cognitect.aws.credentials/CredentialsProvider
                           (fetch [_]
                             (adgoji.aws.creds/creds->aws-api-creds creds)))}))

;; Explore ops
(keys (aws/ops client))

;; Choose op, an read doc
(aws/doc client :AssumeRole)


;; Try to invoke the API via the cognitect client

(def my-role-arn "arn:aws:iam::**:role/MyRole")
(aws/invoke client {:op :AssumeRole :request {:RoleSessionName "me"
                                              :RoleArn my-role-arn}})
;; If it works, create a request template by printing it to the repl and pasting it below

(prn (request-template client :sts :us-east-1 {:op :AssumeRole :request {:RoleSessionName :args/role-session-name
                                                                         :RoleArn :args/role-arn}}))

(def assume-role-template
  {:auth-info {:region "us-east-1", :service "sts", :hostname "sts.amazonaws.com"},
   :request {:request-method :post, :scheme :https, :server-port 443, :uri "/",
             :headers {"content-type" "application/x-www-form-urlencoded; charset=utf-8"},
             :body "Action=AssumeRole&Version=2010-05-15&=%7B%3ARoleSessionName%20%3Aargs%2Frole-session-name%2C%20%3ARoleArn%20%3Aargs%2Frole-arn%7D",
             :hostname "sts.amazonaws.com"}})

;; Create a function that uses the template

(defn assume-role [creds {:keys [role-arn session-name]}]
  (http-request (aws-requests/request-template->request-map creds
                                                            assume-role-template
                                                            {:region :us-east-1
                                                             :args/role-arn role-arn
                                                             :args/role-session-name session-name})))

;; Try it:
(assume-role (adgoji.aws.creds/creds->simple-aws-api-creds creds) {:role-arn my-role-arn
                                                                   :session-name "me"})

;; TODO parse the XML response

;;  Another example:

(def client (aws/client {:api :cloudformation
                             :credentials-provider
                             (reify cognitect.aws.credentials/CredentialsProvider
                               (fetch [_]
                                 (adgoji.aws.creds/creds->aws-api-creds creds)))}))


(keys (aws/ops client))

;; Choose op, an read doc
(aws/doc client :DescribeStacks)

(aws/invoke client {:op :DescribeStacks :request {:StackName "my-stack" }})

(aws/invoke client )

(prn (request-template client :cloudformation :us-east-1 {:op :DescribeStacks :request {:StackName :args/stack-name}}))

(def describe-stacks-template
  {:auth-info {:region "us-east-1", :service "cloudformation", :hostname "cloudformation.us-east-1.amazonaws.com"},
   :request {:request-method :post, :scheme :https, :server-port 443, :uri "/",
             :headers {"content-type" "application/x-www-form-urlencoded; charset=utf-8"},
             :body "Action=DescribeStacks&Version=2010-05-15&StackName=%3Aargs%2Fstack-name",
             :hostname "cloudformation.us-east-1.amazonaws.com"}})

(defn describe-stack [creds stack-name]
  (http-request (aws-requests/request-template->request-map creds
                                                            describe-stacks-template
                                                            {:region :us-east-1
                                                             :args/stack-name stack-name})))
(describe-stack creds "my-stack")

;; TODO parse the XML response
