(ns ccda-to-fhir.examples
  ( :require
    [ccda-to-fhir.template :as template]
    [clojure.data.zip.xml :as x] 
    [clojure.data.xml :as xml]
    [clojure.zip :as zip]
    [clojure.data.zip :as dz]))

(def files
  [
   "Greenway Samples/26775_ExportSummary_CCDA.xml"
   "EMERGE/Patient-0.xml"
   ])

(def resolvers {
           :josh-local ["/home/jmandel/smart/sample_ccdas/" identity]
           :github [
                 "https://raw.githubusercontent.com/chb/sample_ccdas/master/"
                 #(clojure.string/replace % #" " "%20")]
           })

(defn get-file [k path]
  (let [r (resolvers k)
        prefix (get r 0)
        xform (get r 1)
        ret (->> path xform (str prefix))]
    ret))

(def parse-file #(->> % (get-file :github) clojure.java.io/input-stream xml/parse zip/xml-zip))

(println "Parsing files")
(def parsed-files (map parse-file files)) 
