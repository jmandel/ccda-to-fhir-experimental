(ns ccda-to-fhir.template
  ( :require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.data.zip.xml :as x] 
    [clojure.walk :as walk]
    [ccda-to-fhir.fhir :as fhir]
    [clojure.data.xml :as xml]
    [clojure.zip :as zip]
    [clojure.data.zip :as dz]))

(defn from-edn
  [fname]    
  (with-open [rdr (-> (io/resource fname)
                      io/reader
                      java.io.PushbackReader.)]
    (edn/read rdr)))

(def loaded (from-edn "templates.edn"))
(def oids (loaded :oids))
(def base-definitions (loaded :templates))
(def contexts (loaded :contexts))

(defn merge-mixin [v]
  (let [mixin-path (:mixin v)
        mixin-props (get (base-definitions mixin-path) :props)
        new-props
        (reduce (fn [acc prop]
                  (let [xpath (str (:xpath v ) "/" (:xpath prop))
                        fhir-path (get-in v [:fhir-mapping :path])
                        dt (get-in prop [:fhir-mapping :path])
                        fhir-path (fhir/concrete-path-for-dt fhir-path dt)]
                    (conj acc (-> prop
                                  (assoc :xpath xpath)
                                  (assoc-in [:fhir-mapping :path] fhir-path)))))
                []
                mixin-props)]
    {:splice new-props} ; marker so the splicing augmenter later
                        ; knows to incorporate these into array.
    ))

(def augmenters
  [{
    :recognize-by vector?
    :transform-with
    (fn [v]
      (->> v
           (mapcat (fn [x] ( or (get x :splice) [x])))
           vec))}

   {
    :recognize-by #(get % :mixin)
    :transform-with merge-mixin
    }])

(defn augment-def [one-def]
  (reduce (fn [acc, augmenter]
            (let [predicate (augmenter :recognize-by)
                  transform (augmenter :transform-with)]
              (if (predicate acc)
                (transform acc)
                acc
                )))
          one-def
          augmenters))

(def definitions  (walk/postwalk augment-def base-definitions))
