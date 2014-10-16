(ns ccda-to-fhir.examples
  ( :require
    [ccda-to-fhir.template :as template]
    [clojure.data.zip.xml :as x] 
    [clojure.data.xml :as xml]
    [clojure.zip :as zip]
    [clojure.data.zip :as dz]))

(def files
  [
   "Mirth Corporation/Connectathon Samples/unverified/CONTENT_CREATOR_Iatric_IAT C-CDA/1999/3618_Content_Creator_C-CDA_CCD.CONTENT_CREATOR_Iatric_IAT C-CDA.xml"
   "Greenway Samples/26775_ExportSummary_CCDA.xml"
   "EMERGE/Patient-0.xml"
   ])

;(def base "https://raw.githubusercontent.com/chb/sample_ccdas/master/")
(def base "/home/jmandel/smart/sample_ccdas/")

(def parse-file #(-> (str base  %) clojure.java.io/input-stream xml/parse zip/xml-zip))

(def parsed-files (map parse-file files)) 



(defn codes [p] ( x/xml-> p :code)) 

(let [[dot & components] (clojure.string/split "./test/a[@b='okay']" #"/")]
  (if-not (= dot ".")
    (throw ( Exception. "Only paths starting with ./ are supported -- not: " dot)))
  (->> components
       (mapcat
        (fn [component]
          (let [attr-match (re-find #"(.*?)\[@(.*?)='(.*?)'\]" component)
                attr (re-find #"@(.*)" component)
                component (keyword component)]
            (cond
             attr-match [(keyword (attr-match 1)) [(x/attr= (keyword (attr-match 2)) (attr-match 3))]]
             attr [(x/attr (keyword (attr 1)))]
             :else [component]))))))
