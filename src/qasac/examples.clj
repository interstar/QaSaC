(ns qasac.examples
  (:require
   [qasac.saq :refer [ISaqStack ->SaqStack]]
   [qasac.saqmachine :refer [make-SaqMachine]]
            [clojure.core.async :as a
             :refer [>! <! >!! <!! go chan buffer close! thread go-loop
                     alts! alts!! timeout]])
  )


(defn pump [col out-chan]
  (go-loop [xs col]
    (if (empty? xs) '()
        (do
          (>! out-chan (first xs))
          (recur (rest xs) ))) ))


(defn pass-through [cix co]
  {:s1 (make-SaqMachine [0 :/// :DUP :-> 1 :+ ] {} {:-> cix})
   :pass (make-SaqMachine [:X :->] {:X cix} {:-> co})
   :out co
   } )

(defn add-two-streams [cix ciy co]
  {:s1 (make-SaqMachine [0 :/// :DUP :-> 1 :+ ] {} {:-> cix})
   :s2 (make-SaqMachine [1 :-> 2 :-> 3 :->] {} {:-> ciy})
   :adder (make-SaqMachine [:X :Y :+ :->] {:X cix :Y ciy} {:-> co})
   :out co
   })

(defn filter-even [cix co]
  {:s1 (make-SaqMachine [0 :/// :DUP :-> 1 :+ ] {} {:-> cix})
   :filter (make-SaqMachine [:X :DUP [:->] :SWAP 2 :% 0 :=  [:DROP] :SWAP  :COND   ]  {:X cix} {:-> co})
   :out co
   })

(defn filter-split [cix m1 m2 co]
  {:s1 (make-SaqMachine [0 :/// :DUP :-> 1 :+ ] {} {:-> cix})
   :filter (make-SaqMachine [:X :DUP [:->] :SWAP 2 :% 0 :=  [:+>] :SWAP  :COND   ] {:X cix} {:-> m1 :+> m2})
   :even (make-SaqMachine [[:even ] :X :CONS :->] {:X m1} {:-> co})
   :odd (make-SaqMachine [[:odd ] :X :CONS :->] {:X m2} {:-> co})
   :out co
   }
  )

(defn bunch-of-five [cix co]
  {:s1 (make-SaqMachine [0 :/// :DUP :-> 1 :+ ] {} {:-> cix})
   :bunch (make-SaqMachine [[] [:X :CONS] 5 :REP :->] {:X cix } {:->  co})
   :out co
   })

(defn unbundle [cix co]
  {:s1 (pump (repeat [1 2 3]) cix)
   :unbundle (make-SaqMachine [:X :UNQ [:->] 3 :REP] {:X cix} {:-> co} )
   :out co
   }  )

(defn fizzbuzz [cix co]
  {:s1 (make-SaqMachine [0 :/// :DUP :-> 1 :+ ] {} {:-> cix})
   :fizzbuzz (make-SaqMachine [:X :DUP  ["FizzBuzz" :-> :DROP ]
                               [:DUP
                                ["Buzz" :-> :DROP] [:DUP ["Fizz" :-> :DROP] [:->] [3 :% 0 :=] :COND]
                                [5 :% 0 :=] :COND]
                               [15 :% 0 := ] :COND] {:X cix} {:-> co})
   :out co
   })
