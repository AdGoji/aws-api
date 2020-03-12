(defn get-deps [deps-file allowed known]
  (let [{:keys [deps]} (try (clojure.edn/read-string (slurp deps-file)) (catch Exception e (throw (ex-info (str "Error in file " deps-file) {} e))))
        deps (apply dissoc deps known)
        {:keys [mvn local] :as m} (group-by (fn [[k v]]
                                              (cond
                                                (:mvn/version v) :mvn
                                                (:local/root v) :local
                                                :else :unsupported))
                                            deps)]
    (when-let [unsupported (seq (clojure.set/difference (set (keys m)) allowed))]
      (throw (ex-info "Found unsupported deps" {:deps (select-keys m unsupported)})))

    {:deps (mapv (fn [[k v]]
                  (cond-> [k (:mvn/version v)]
                    
                    (:exclusions v) 
                    (conj :exclusions (:exclusions v))))
                mvn)

     :source-roots (mapv (fn [[k v]]
                               (:local/root v))
                         local)}))

(let [{:keys [deps source-roots]} (get-deps "deps.edn" #{:mvn :local} nil)]
  (def deps (into deps (mapcat (fn [root]
                                  (:deps (get-deps (str root "/deps.edn") #{:mvn :local} (map first deps))))
                               source-roots)))
  (def source-paths (vec (cons "src" (map (fn [root]
                                            (str root "/src"))
                                          (concat source-roots
                                                  (mapcat (fn [root]
                                                             (:source-roots (get-deps (str root "/deps.edn") #{:mvn :local} (map first deps))))
                                                           source-roots)))))))

(defproject adgoji.aws.api (or (System/getenv "VERSION") "none")
  :dependencies ~deps

  :plugins [[nrepl/lein-nrepl "0.3.2"]]

  ;:repl-options {:init-ns app.scratch}

  :min-lein-version "2.8.1"

  ; :auto-clean false
  :resource-paths ["resources"]
  :source-paths ~source-paths
  :test-paths []

  :clean-targets ^{:protect false} ["resources/public/js/compiled"
                                    "target"
                                    "test/js"]
  
  :profiles {:dev     {:dependencies [[com.cognitect.aws/api       "0.8.445"]
                                      [com.cognitect.aws/endpoints "1.1.11.732"]
                                      [com.cognitect.aws/s3        "784.2.593.0"]
                                      [com.cognitect.aws/sts       "773.2.578.0"]
                                      [com.cognitect.aws/sts       "773.2.578.0"]
                                      [com.cognitect.aws/cloudformation "773.2.575.0"]
                                      
                                      [borkdude/babashka "0.0.75" :exclusions [org.clojure/clojure 
                                                                               org.clojure/core.async
                                                                               org.clojure/spec.alpha
                                                                               org.clojure/core.memoize
                                                                               
                                                                               
                                                                               ]]
                                      [org.clojure/clojure "1.10.2-alpha1"]
                                      
                                      ]
                       :plugins [[lein-kibit "0.1.8"]]
                       :global-vars  {*warn-on-reflection* true
                                      *assert*             true}}})  