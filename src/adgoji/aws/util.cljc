(ns adgoji.aws.util)

(let [sentinel ::sentinel]
  (defn getx [m k]
    (let [ret (get m k sentinel)]
      (if (identical? ret sentinel)
        (if (map? m)
          (throw (ex-info (str "Could not find " (pr-str k)
                               (when (sequential? k)
                                 ", are you sure you didn't mean getx-in?"))
                          {:keys-of-map (keys m)
                           :key k
                           :m m}))
          (throw (ex-info "Map expected" {:m m
                                          :k k})))
        ret))))
