(ns qasac.core
  (:require
   [qasac.saq :refer [ISaqStack ->SaqStack]]
   [qasac.saqmachine :refer [make-SaqMachine]]
   [clojure.core.async :as a
    :refer [>! <! >!! <!! go chan buffer close! thread go-loop
            alts! alts!! timeout]]
   [qasac.examples :as examples]
   [clojure.string :refer [split split-lines join trim]]   )
   (:gen-class))


(defn printer [in-chan n]
  (loop [counter n]
    (if (= counter 0) (close! in-chan)
        (do
          (println (<!! in-chan))
          (recur (dec counter))) )
    ) )

(defn collector [in-chan n]
  (loop [counter n xs []]
    (if (zero? counter)
      xs (recur (dec counter) (conj xs (<!! in-chan) ))) )
  )

(defn pit
  ([x] (pit x ""))
  ([x label] (do (println label x) x)))

(defn gather [pred line-list]
  (as-> line-list v
        (filter pred v)
        (map #(->> %1 rest (apply str) trim) v)
        (join " " v)
        (split v #"\s+")       ))




(defn prepare-script [s]
  (->> s (split-lines) (filter #(not= "" %1)) (filter #(not= \# (first %1))) ))

(defn check-required-channels "Check that the given-chans contains everything that the script requires "
    [script given-chans]
    (let [
          ;; channels that the calling evaluator needs to provide
          chan-reqs (gather #(= \! (first %1)) script )

          ;; produce list of errors for any channel specified in program, but not given to the evaluator
          chan-errors (as-> chan-reqs v
                            (filter #(not (contains? given-chans (symbol  %1))) v)
                            (map #(str "No required channel \"" %1 "\" given") v))
        ]

      chan-errors ))

(defn make-channels "Make the rest of the channels declared in the script and return all channels in "
  [script given-chans]
  (let [
        ;; now collect names of new channels we'll define
        chan-defs (gather #(= \= (first %1)) script)

        ;; now make the new chan-defs into actual channels
        chans (reduce (fn [chans new-chan-name] (conj chans {(symbol new-chan-name) (chan)} ))
                      given-chans chan-defs)]
    chans))

(defn node-defs "The lines that define the nodes"
  [script]
  (->> script
       (filter #(not (= \! (first %1))))
       (filter #(not (= \= (first %1)))) ))

(defn execute
  ;; text format of QaSaC programs ...
  ;; ! channels it expects to be given by this execute program
  ;; = new channels the program creates
  ;; from then on each pair of lines are
  ;; {in-chans} {out-chans}
  ;; code
  ;;
  ;; Code can contain blank lines and comments on lines that start with #
  ;; Apart from that, this interpreter is pretty fragile

  ([s] (execute s {} ))
  ([s given-chans]
     (let [script (prepare-script s)
           chan-errors (check-required-channels script given-chans)


           node-script (node-defs script)]
       (cond (empty? script) {:errors ["Empty Program"]}
             (not (empty? chan-errors)) {:errors chan-errors}
             (empty? node-script) {:errors ["No nodes defined"]}
             :else
             (let [chans (make-channels script given-chans)
                   nodes (loop [xs node-script build [] ]
                           (if (empty? xs) build
                               (let [[ins outs] (->> (str "(" (first xs)  ")") (read-string) )
                                     code (->> (str "(" (first (rest xs)) ")") (read-string) )

                                     getchan (fn [[k v]]
                                               (let [c (get chans v)] {k c}))
                                     tins (reduce-kv (fn [m k v] (let [c (get chans v)]
                                                                  (conj m {k c})) ) {} ins)
                                     touts (reduce-kv (fn [m k v] (let [c (get chans v)]
                                                                   (conj m {k c})) ) {} outs)
                                     newbuild (cons
                                               (make-SaqMachine code tins touts)
                                               build )]
                                 (recur (rest (rest xs)) newbuild )
                                 )))]
               {:chans chans :nodes nodes :errors []})) )))



;; main interpreter ... arg1 which program, arg2 how many outputs, default 20
(defn -main [& args]
  (let [res (execute (slurp (first args)) {(symbol "out") (chan)})
        errors (:errors res)]
    (if (not (empty? errors)) (println errors)
        (printer ((symbol "out") (:chans res))
                 (if (< 1 (.size args)) (read-string  (second args)) 10 ) ))))
