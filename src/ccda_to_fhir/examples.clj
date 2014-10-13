(ns ccda-to-fhir.examples
  ( :require
    [ccda-to-fhir.template :as template]
    [clojure.data.xml :as xml]
    [clojure.zip :as zip]
    ))

(def files
  [
   "/home/jmandel/smart/sample_ccdas/EMERGE/Patient-0.xml"
   "/home/jmandel/smart/sample_ccdas/Greenway Samples/26775_ExportSummary_CCDA.xml"
   "/home/jmandel/smart/sample_ccdas/Mirth Corporation/Connectathon Samples/unverified/CONTENT_CREATOR_Iatric_IAT C-CDA/1999/3618_Content_Creator_C-CDA_CCD.CONTENT_CREATOR_Iatric_IAT C-CDA.xml"

   ])

(def parse-file #(-> % clojure.java.io/input-stream xml/parse))

(def parsed-files (map parse-file files)) 

