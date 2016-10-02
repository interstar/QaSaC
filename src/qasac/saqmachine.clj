(ns qasac.saqmachine
  (:require [clojure.core.async :as a
             :refer [>! <! >!! <!! go chan buffer close! thread go-loop
                     alts! alts!! timeout]]
            [clojure.string :refer [split]]
            [qasac.saq :as saq
             :refer [->SaqStack]])
  )




(defn f2 "Perform a two argument function on the top two items of the stack"
  [stack f]
  (let [[x1 s1] (.pop stack)
        [x2 s2] (.pop s1)]
    (.push s2 (f x2 x1 )))  )

(defn f3 "Three argument function on the top three items of the stack"
  [stack f]
  (let [[x1 s1] (.pop stack)
        [x2 s2] (.pop s1)
        [x3 s3] (.pop s2)]
    (.push s3 (f x3 x2 x1 )))
  )

(defn f4 "Four argument function on the top four items of the stack"
  [stack f]
  (let [[x1 s1] (.pop stack)
        [x2 s2] (.pop s1)
        [x3 s3] (.pop s2)
        [x4 s4] (.pop s3)]
    (.push s4 (f x4 x3 x2 x1 )))
  )

(declare saq-machine-eval)

(defn saq-unq [stack in-chans out-chans]
  (let [[xs s] (.pop stack)]
    (if (coll? xs)
      (reduce
       (fn [s x] (saq-machine-eval s x in-chans out-chans))
       s xs)
      stack) ) )

(defn saq-expl [stack]
  (let [[strn s1] (.pop stack)
        xs (split strn #"\s+") ]
    (.push s1 (map read-string xs)) ))

(defn saq-stplcnt [stack]
  (let [[tpl s1] (.pop stack)
        cnt (->> tpl (seq) (filter #(= \% %)) (count))]
    (.push s1 cnt)))

(defn saq-stpl [stack]
  (let [[tpl s1] (.pop stack)
        cnt (->> tpl (seq) (filter #(= \% %)) (count))
        [xs s2] (.popn s1 cnt)
        res (apply format (cons tpl xs) )]
    (.push s2 res))
  )

(defn saq-tplcnt [stack]
  (let [[tpl s1] (.pop stack)
        cnt (->> tpl (filter #(= :$$ %)) (count))]
    (.push s1 cnt)))

(defn saq-tpl [stack]
  ;; With list templates, we fill from the stack ... but if we don't have enough items we don't complain
  ;; just leave it partially applied
  (let [[tpl s1] (.pop stack)]
    (loop [tps  tpl stack s1 build []]
      (if (empty? tps) (.push stack build)
          (let [tt (first tps) trst (rest tps)]

            (cond (empty? stack) (.push stack (concat build tps))
                  (= :$$ tt) (let [[v s2] (.pop stack)] (recur trst s2 (conj build  v)) )
                  (vector? tt) (let [[v s2] (-> stack (.push tt) (saq-tpl) (.pop) )]

                               (recur trst s2 (conj build v)))
                  :else (recur trst stack (conj build tt) )
                  )))
      )))

; [true-block] [false-block] [test] :COND
(defn saq-cond [stack in-chans out-chans]
  (let [ [[yes no test] s2] (.popn stack 3)
         s3 (saq-unq (.push s2 test) in-chans out-chans)
         [testval s4] (.pop s3 )]
    (case  testval
      true (saq-unq (.push s4 yes) in-chans out-chans)
      (saq-unq (.push s4 no) in-chans out-chans) ))  )

(defn saq-rep [in-stack in-chans out-chans]
  (let [[[block n] r-stack] (.popn in-stack 2)  ]
    (loop [count n stack r-stack]
      (if (zero? count) stack
          (recur (- count 1)
                 (reduce (fn [s x] (saq-machine-eval s x in-chans out-chans))
                         stack block) )  )
      ) ))

(defn saq-linrec-r [stack test base inward outward in-chans out-chans]
  (let [s2 (.push stack test)
        s3 (saq-unq s2 in-chans out-chans)
        [val s4] (.pop s3)]

    (if val (saq-unq (.push s4 base) in-chans out-chans)
        (-> s4 (.push inward) (saq-unq in-chans out-chans)
            (saq-linrec-r test base inward outward in-chans out-chans)
            (.push outward) (saq-unq in-chans out-chans) ))))

(defn saq-linrec [stack in-chans out-chans]
  (let [[[test base inward outward] s] (.popn stack 4)]
    (saq-linrec-r s test base inward outward in-chans out-chans) ))




(defn saq-handle-future [stack op in-chans out-chans]
  (let [[[args block count fut] s1] (.popn stack 4)]
    (cond (> count 1) ;; recur
          (-> s1 (.push (cons op args)) (.push block) (.push (- count 1)) (.push :FUT))
          (> count 0)
          (-> s1
              (.push (reverse (cons op args)))
              (saq-unq in-chans out-chans)
              (.push block)
              (saq-unq in-chans out-chans))
          :else (.push s1  (str  "ERROR ... Future counter <= 0 " count " " args "  " block))
      )))


(defn saq-machine-eval
  ([stack op] (saq-machine-eval stack op {} {}) )

  ([stack op in-chans out-chans]
     (if (= (.peek stack) :FUT)
       (saq-handle-future stack op in-chans out-chans)
       (case op
         ;; The null operation
         :CHILL stack

         ;; Input channels (X, Y and Z)
         :X (.push stack (<!! (:X in-chans)))
         :Y (.push stack (<!! (:Y in-chans)))
         :Z (.push stack (<!! (:Z in-chans)))

         ;; Output channels -> and +>
         :-> (let [[val new-stack] (.pop stack)]
               (>!! ( :-> out-chans) val)
               new-stack)

         :+> (let [[val new-stack] (.pop stack)]
               (>!! ( :+> out-chans) val)
               new-stack)

         ;; Basic arithmetic
         :+ (f2 stack +)
         :- (f2 stack -)
         :* (f2 stack * )
         :/ (f2 stack /)
         :< (f2 stack <)
         :> (f2 stack >)
         :% (f2 stack mod) ;; modulus
         := (f2 stack =) ;; equality

         ;; Basic Stack manipulation
         :DUP (.dup stack) ;; duplicate top
         :SWAP (.swap stack) ;; swap top two
         :DROP (.drop stack) ;; drop the top
         :NIP (.nip stack) ;; nip (ie. remove the second item from the stack)
         :TUCK (.tuck stack) ;; copy top item under second item on stack


         ;; Strings and Lists
         :UNQ (saq-unq stack in-chans out-chans) ;; unquote a list (eval all items)
         :EXPL (saq-expl stack ) ;; explode a string into a list (spaces are split separators)

         :CONS (f2 stack
                   (fn [xs x]
                     (if (coll? xs) (cons x xs)
                         (str "Tried to cons " x " onto non-collection " xs)
                         )))

         :STPL (saq-stpl stack ) ;; string template in a string filled from stack
         :STPLCNT (saq-stplcnt stack) ;; count the number of empty slots in a string template

         :TPL (saq-tpl stack ) ;; template (fill items in list from stack)
         :TPLCNT (saq-tplcnt stack) ;; count the number of empty slots in a template

         ;; Combinators
         :COND  (saq-cond stack in-chans out-chans) ;; if then else
         :REP (saq-rep stack in-chans out-chans) ;; repeat n times
         :LINREC (saq-linrec stack in-chans out-chans) ;; linear recursion


         ;; Diagnosis
         ;;; pop-print ... prints (and loses) the top of the stack
         :PPT (let [[v s2] (.pop stack)]
                (println v)
                s2)

         ;;; prt the stack (untouched)
         :PRT (do
                (println stack "Size: <" (.len stack) ">  Top: " (.peek stack) )
                stack)


         ;; When in doubt ... just put it on stack
         (.push stack op)))
     )  )

(defn preparse [code]
  (if (.contains code :///)
    (do
      (let [[a b] (split-with (complement #{:///}) code)]
        (concat a (cycle (rest b))) ))
    (cycle code)
    ) )

(defn make-SaqMachine [code in-chans out-chans ]
  (thread
    (loop [stack (->SaqStack [])
           code-loop (preparse code)]
      (let [op (first code-loop)
            continue (rest code-loop)]
        (recur (saq-machine-eval stack op in-chans out-chans) continue))  )) )
