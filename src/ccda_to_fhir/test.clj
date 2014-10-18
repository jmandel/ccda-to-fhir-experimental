(ns ccda-to-fhir.test
  ( :use [ccda-to-fhir.core])
  ( :require
[clojure.data.zip :as dz]
    [clojure.zip :as zip]
    [clojure.data.xml :as xml]
    [clojure.data.zip.xml :as x] 
    [ccda-to-fhir.template :as template]
    [ccda-to-fhir.examples :as examples]
    [clojure.pprint :as pprint])
  )


(defn -main [& args]
    (time (pprint/pprint (doall (parse-ccda (first examples/parsed-files))))))
