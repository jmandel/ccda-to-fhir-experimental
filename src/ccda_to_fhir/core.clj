(ns ccda-to-fhir.core
  ( :require
    [clojure.pprint :as pprint]
    [ccda-to-fhir.template :as template]
    [ccda-to-fhir.examples :as examples]
    [ccda-to-fhir.fhir :as fhir]
    [clojure.data.zip.xml :as x] 
    [clojure.data.xml :as xml]
    [clojure.zip :as zip]
    [clojure.data.zip :as dz]))

(def auto (partial dz/auto true))
(def calls ( atom {:text 0 :follow 0}))

(defn -all-nodes [d]
  (->> d dz/descendants (map auto)))

(defn by-template [d t]
  (->> d -all-nodes
       (mapcat #(x/xml-> % [:templateId (x/attr= :root t)]))))


(defn parse-iso-time [t]
  (when t
    (if-let [pieces (re-find #"^(....)(..)?(..)?" t)]
      (apply str (interpose "-" (remove nil? (rest pieces)))))))

(defmulti to-fhir-dt (fn [dt ccda-node] dt))

(defmethod to-fhir-dt "string" [dt ccda-node]
  (println "Converting -> string " (type ccda-node))
  (cond
   (nil? ccda-node) nil
   (= java.lang.String  (type ccda-node)) ccda-node 
   :else (do 
        (swap! calls update-in [:text] #(+ % 2))
        (let [valattr (x/xml1-> ccda-node (x/attr :value))
              text (x/xml1-> ccda-node x/text)]

         (println "Converted -> string " valattr text)
          (if valattr (str "valattr was" valattr) text)))))

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

                                        ; 
                                        ;  A mapping context consists of... 
                                        ;  1. C-CDA context object (represents context-conduction behavior) -- consider this as atom for *binding*
                                        ;  2. C-CDA node "in focus" (which also links back to the whole doc)
                                        ;  3. Set of FHIR feed entries that have been created so far (?)
                                        ;  4. FHIR resource currently in the process of being created (?)
                                        ;
                                        ;  The primitive operation takes a mapping context and returns a set of "Candidate results".
                                        ;  Each candidate result contains:
                                        ;
                                        ;  - metadata-tagged FHIR structure -- meta with
                                        ;     :as [:Type | :Resource | :Primitive]
                                        ;     :path "Observation.appliesDateTime" <-- note this resolves [x] notation to fully specify value
                                        ;
                                        ;  - set of newly-created dependent resources for inclusion in feed upstream
                                        ;
                                        ;  The process by which candidate results are created is:
                                        ;
                                        ;  1. Evaluate all the types that are allowed for :fhir-mapping :path
                                        ;
                                        ;  2. For each type allowed, either
                                        ;     a. parsePrimitive on the current path, or
                                        ;     b. recursively map-context on the current node
                                        ;        using the appropriate C-CDA "template" maper.
                                        ;
                                        ;
                                        ;
                                        ;

                                        ;

                                        ;  (fill-in-temlate)  is a recursive function that ping-pongs with (map-context).
                                        ;
                                        ;  To build up results, iterate over a template's props, and (map-context) each of them.
                                        ;  For each result, determine which candidate mappings(s) to keep for mering.
                                        ;
                                        ;  * there is an opportunity here to apply "fix-ups" between (map-context) and merging in.
                                        ;  With luck these can be declarative lists of fix-ups specified in the mapping EDN
                                        ;
                                        ;  Then initialize a default strucutre and merge in each of the prop-level results.
                                        ;  Finally, for each selected candidate, merge all dependencies into the feed.


(defn fhir-path-to-kw [p]
  (keyword (str "fhir-type-" p)))

(def primitives #{"string" "code" "dateTime" "uri" "time"})

(defn default-template-for [template mapping-context dt]
  (let [resolver (get-in mapping-context [:resolve-with])
        link (get-in template/definitions [resolver dt])
        linked-path (get-in link [:fhir-mapping :path])
        path (get-in mapping-context [:fhir-mapping :path])
        ppath (keyword (str "fhir-type-" path))
        tpath(keyword (str "fhir-type-" dt))]

    (println "DECIDING ON" template mapping-context dt)
    (cond
     template {:template  (template/definitions template)}
     link (merge link (default-template-for nil link linked-path))
     resolver false ; resolver existed but didn't suppor this datatype
                    ; -> nothing to do.
     (primitives path) {:primitive path :path path}
     (primitives dt) {:primitive dt :path path}
     (= "internal-node" dt) false
     (= dt "Type")  {:template  (template/definitions ppath)}
     :else  {:template (template/definitions tpath)})))

(defn follow-path [ccda-nodes path]
  (println "following from " (count ccda-nodes) "to " path)
  (println (map :attrs (map zip/node ccda-nodes)))
  (swap! calls update-in [:follow] inc)
  (let [ret
        (mapcat  #(apply x/xml-> % path) ccda-nodes)]
    (println "followed to get " (count ret))
    ret
    ))

(declare map-context)
(def counter (atom 1))

(defn path-to-zipq [p]
  (println "handle quer " (type p) p)
  (let [[dot & components] (clojure.string/split p #"/")]
    (->> components
         (mapcat
          (fn [component]
            (let [attr-match (re-find #"(.*?)\[@(.*?)='(.*?)'\]" component)
                  attr (re-find #"@(.*)" component)
                  component (keyword component)]
              (cond
               attr-match (let [
                                tag (keyword (attr-match 1))
                                tag (if (empty? (attr-match 1)) nil tag)
                                attr (keyword (clojure.string/replace (attr-match 2) #":" "/"))
                                value (attr-match 3)
                                ]
                            (remove nil? [tag [(x/attr= attr value)]]))
               attr [(x/attr (keyword (attr 1)))]
               :else [component]))))))
  #_(if-let [parts (re-matches #"(.*)\[(.*?)\]\/?(.*)" p)]
      (concat [(parts 1)] [(xpath-to-zipq (parts 2))]  [( parts 3)])
      ))

(defn restrict-query-to-template [q t]
  (let [tid (template/oids t)]
                                        ;(println "q type" (type q) tid)
    (if tid (conj (vec q) [:templateId (x/attr= :root tid)])
        q)))

(defn to-zipq [mapping-context prop]
  (println "to zipq " prop)
  (let [xpathq (prop :xpath)
        _ (println "xpq" xpathq)
        prefix (get mapping-context :xpath-prefix)
        _ (println "prefix" prefix)
        baseq (xpath-to-zipq xpathq)
        prefixq (when prefix (xpath-to-zipq prefix))
        baseq (concat prefixq baseq)
        template (prop :template)]
    (restrict-query-to-template baseq template)))

(defn pick-best-candidate [prop base candidate-vals]
  (let  [candidates
         (doall (->> candidate-vals
                     (remove #(or (empty? %) (empty? (get  % :value))))))]

                                        ; need a real way to pick -- not just "the one with the mostest stuff"
    (println "considering " (count candidates) candidates)
    ;(apply max-key #(count (pr-str %)) candidates)
    (if (< 1  (count candidates ))
      (do
        (println "failing for candidates set")
        (pprint/pprint candidates)
        (throw (Exception. "Can't decide among >1 candidate")))
      )
    (if (= 0 (count candidates))
      (println "No candiets for " prop base candidate-vals)
      )
    (when candidates (first candidates))
    ))

(defn merge-points-compatible [a b]
  (let [possibilities (fhir/datatypes-for-path a)]
                                        ;((set (possibilities :types)) b)
    true
    ))


(defn last-path-component [p]
  (first (reverse (clojure.string/split p #"\."))))

(defn merge-val-into-fhir [prop base candidate-vals]
  (let [merge-point (get-in prop [ :fhir-mapping :path])
        candidate-vals (doall candidate-vals)
                                        _ (println "pick best" merge-point " among " candidate-vals "...") 
        best-candidate (pick-best-candidate prop base candidate-vals)
                                        ;_ (println "merge-point best " best-candidate)
                                         proposed-merge-point (best-candidate :path)
                                        ;compatible (merge-points-compatible merge-point proposed-merge-point)
        ]

    #_(if-not compatible
        (throw (Exception. (str "Incompatible merge points: " merge-point " vs. " proposed-merge-point ))))
    (assoc base (str (last-path-component proposed-merge-point) (swap! counter inc)) (best-candidate :value))
    (update-in base [(str (last-path-component proposed-merge-point))] (fnil conj [])  (best-candidate :value))

    ))

(defn merge-val-sets-into-fhir [prop base val-sets]
  (reduce (partial merge-val-into-fhir prop) base val-sets))

(defn fill-in-template [ccda-context ccda-node fhir-in-progress mapping-context]
  (println "filling in for"  mapping-context  "with context " fhir-in-progress)

  (let [
        top-level-object (get-in mapping-context [:template :fhir-mapping :path])
        props (get-in mapping-context [:template :props])
        all-props
        (reduce (fn [acc prop]
                  (let [zipq (to-zipq mapping-context prop)
                        at-path (follow-path [ccda-node] zipq)
                        filled (map-context ccda-context at-path acc prop )]
                    (if (> (count filled) 0 )
                      (merge-val-sets-into-fhir prop acc  filled)
                      acc))
                  )  {} props)]
    
    (when-not (empty? all-props )
      (if top-level-object {:fhir-path top-level-object :fhir-value all-props} all-props))))


(defn concrete-path-for-dt [path dt]
  (clojure.string/replace path #"\[x\]" (clojure.string/capitalize dt)))

(defn map-context
  "For each starting node, returns a vector of FHIR data (primitives, compounds, or resources) each
   meta-tagged to indicate their FHIR type (e.g. {:path \"CodeableCon/orkingcept\"})"
  [ccda-context ccda-nodes fhir-in-progress mapping-context]
  (println "mapping context for " (count ccda-nodes) mapping-context)
  (let [fhir-path (get-in mapping-context [:fhir-mapping :path])
        fhir-dts (fhir/datatypes-for-path fhir-path)
        fhir-dts (fhir-dts :types)
        _ (println  fhir-path "Types are " fhir-dts)
        fhir-dts (if (pos? (count fhir-dts))  fhir-dts ["internal-node"])
        template (mapping-context :template)]
    (doall (for [node ccda-nodes] 
             (for [dt fhir-dts 
                   :let [fhir-path (concrete-path-for-dt fhir-path dt)
                         template (default-template-for template mapping-context dt)]]
               (do
                 (println "tempalte of" template " against " fhir-path dt (type node) fhir-path (:path template))
                 (cond 
                  (:primitive template)(let [prefix (:xpath-prefix template)
                                             further (if prefix (to-zipq nil {:xpath prefix}))
                                             nextnode (if prefix (first (follow-path [node] further)) node)]
                                         {:path fhir-path :value (to-fhir-dt (:primitive template) nextnode)})
                  (:template template) (do ;(println "doing for template " fhir-path template)
                             {:path fhir-path
                              :value  (fill-in-template ccda-context node mapping-context template)})
                  :else (println "no tempalte found for " dt )
                  )))))))
