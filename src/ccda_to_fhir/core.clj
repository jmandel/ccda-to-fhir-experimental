(ns ccda-to-fhir.core
  ( :use [ccda-to-fhir.query :only [follow-property follow-xpath]])
  ( :require
    [clojure.pprint :as pprint]
    [ccda-to-fhir.template :as template]
    [ccda-to-fhir.examples :as examples]
    [ccda-to-fhir.fhir :as fhir]
    [clojure.data.zip.xml :as x] 
    [clojure.zip :as zip]))

(defn parse-iso-time [t]
 ; (print "parsing isoetme " t)
  (when t
    (if-let [pieces (re-find #"^(....)(..)?(..)?" t)]
      (apply str (interpose "-" (remove nil? (rest pieces)))))))

(defmulti to-fhir-dt (fn [dt ccda-node] dt))

(defmethod to-fhir-dt "string" [dt ccda-node]
  ;(println "Converting -> string " (type ccda-node))
  (cond
   (nil? ccda-node) nil
   (= java.lang.String  (type ccda-node)) ccda-node 
   :else (let [valattr (x/xml1-> ccda-node (x/attr :value))
               text (x/xml1-> ccda-node x/text)]
           (or valattr text))))

(defmethod to-fhir-dt "uri" [dt ccda-node]
  (to-fhir-dt "string" ccda-node))

(defmethod to-fhir-dt "code" [dt ccda-node]
  (to-fhir-dt "string" ccda-node))

(defmethod to-fhir-dt "identifier" [dt ccda-node]
  (to-fhir-dt "string" ccda-node))

(defmethod to-fhir-dt "dateTime" [dt ccda-node]
  (parse-iso-time (to-fhir-dt "string" ccda-node)))

(defmethod to-fhir-dt "time" [dt ccda-node]
  (to-fhir-dt "string" ccda-node))

(def primitives #{"string" "code" "dateTime" "uri" "time"})

(defn get-context [id]
  (get template/definitions id))

(defn fhir-path-for [context-stack name]
  (str (:fhir (first context-stack)) "." name))

(defn default-context-for [dt desc]
  (let [type-name (or (get desc :reference) dt)]
    (when type-name
      (keyword (str "fhir-type-" type-name)))))

(defn next-context-for [dt desc]
  (let [specified (-> desc (get :content-from) template/contexts)
        default (default-context-for dt desc)
        base (or specified (-> default template/contexts))]
    ;(println "next-context for " base)
    (-> desc
        (merge base)
        (dissoc :content-from)))) 

(declare apply-context)

(defn fill-field-extensions [extensions] "TODO")

(defmulti fill-one-field
  (fn [ccda-resources ccda-node context-stack [name desc]] 
    (if (instance? java.lang.String name) :simple-field name)))

(defmethod fill-one-field :one-of
  [ccda-resources ccda-node context-stack field]
  (let [children (follow-xpath [ccda-node])]
    "TODO"))

(defn fill-one-field-desc 
  [ccda-resources ccda-node context-stack [name desc]]
;  (println "fill one field"  name desc)
  (if-let [fixed-val (get desc :content-fixed)]
    [fixed-val]
    (let [
          fhir-path (fhir-path-for context-stack name)
          dt (-> fhir-path fhir/path-to-datatypes first)
          primitive? (primitives dt)
          extensions (map get-context (get desc :extension))
          next-context (next-context-for dt desc)
        ;  _ (println "filling " next-context fhir-path dt primitive? extensions)
          ]
      (doall
       (for [node (follow-xpath [ccda-node] next-context)]
         (if primitive? {:value (to-fhir-dt dt node)
                         :extension (fill-field-extensions extensions)}
             (do 
               ;(println "next-context to apply" next-context)
                (apply-context ccda-resources node (cons next-context context-stack)))))))))

(defmethod fill-one-field :simple-field
  [ccda-resources ccda-node context-stack [name desc]]
  (let [descs (if (vector? desc) desc [desc])]
    (apply concat
           (for [desc descs]
             (fill-one-field-desc
              ccda-resources
              ccda-node
              context-stack
              [name desc])))))

(def counter (atom 0))

(defn merge-one-field [ ccda-resources context-stack acc [field-name val]]
  (assoc acc (str  field-name ) val))

(defn apply-context-to [kw ccda-resources ccda-node context-stack]
  (let [fill (partial fill-one-field ccda-resources ccda-node context-stack)
        fields (get (first context-stack) kw)
        filled (map fill fields)
        field-names (map first fields)
        filled-map (map vector field-names filled)]
    filled-map))

(defn apply-context [ccda-resources ccda-node context-stack]
  ;(println "Apply context " (first context-stack))
  (let [value-fields (apply-context-to :content ccda-resources ccda-node context-stack)
        extension-fields (apply-context-to :extension ccda-resources ccda-node context-stack)
        merge (partial merge-one-field ccda-resources context-stack)]
    {
     :value (reduce merge {} value-fields)
     :extension (reduce merge {} extension-fields)
     })) 

(defn parse-ccda [root-element]
  (apply-context
   (atom {})
   root-element
   (list (template/contexts :ccda))))
