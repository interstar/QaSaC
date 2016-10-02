(ns qasac.core-test
  (:require [clojure.test :refer :all]
            [qasac.saq :refer [ISaqStack ->SaqStack]]
            [qasac.core :refer [collector execute pit]]
            [qasac.examples :refer [pump]]
            [qasac.saqmachine :refer [saq-machine-eval preparse make-SaqMachine ]]
            [clojure.core.async :refer [chan]]))

(deftest SaqStack
  (testing "SaqStack"
    (let [s1 (->SaqStack [])
          s2 (.push s1 1)
          po2 (.pop s2)
          pe2 (.peek s2)
          s3 (.push s2 2)
          po3 (.pop s3)
          po3a (-> s3 (.pop) (nth 1) (.pop) )
          [xs sx] (.popn s3 2)
          ss1 (.pushn (->SaqStack []) '(:x :y :z) )

          s4 (.push s3 3)

          ]
      (is (= (.len (->SaqStack [])) 0))
      (is (= po2 (list 1 s1)))
      (is (= pe2 1))
      (is (= po3 (list 2 s2)))
      (is (= po3a po2))
      (is (= (.size xs) 2))
      (is (= xs [1 2]))
      (is (= (.popn ss1 3) (list [:x :y :z] (->SaqStack []) )))
      (is (= (.popn ss1 2) (list [:y :z] (->SaqStack [:x]) )))
      (is (= (.peek (.drop s3)) 1))
      (is (= (-> s3 (.dup) (.popn 2) (nth 0)) '(2 2) ))

      (is (= (-> s4 (.nip) (.popn 2) (nth 0)) '(1 3) ))
      (is (= (-> s4 (.tuck) (.popn 3) (nth 0)) '(3 2 3)))

      )))

(deftest SaqMachine-eval
  (testing "SaqMachine-eval"
    (let [stack (->SaqStack [])
          p1 (-> stack
                 (saq-machine-eval 1)
                 (saq-machine-eval 2)
                 (saq-machine-eval :+))
          p2 (-> stack
                 (saq-machine-eval 3)
                 (saq-machine-eval 1)
                 (saq-machine-eval :-))
          p3 (-> stack
                 (saq-machine-eval :a)
                 (saq-machine-eval :b)
                 (saq-machine-eval :SWAP))


          p6 (-> stack
                 (saq-machine-eval [1 2 3])
                 (saq-machine-eval :UNQ))

          p7 (-> stack
                 (saq-machine-eval [3 4 :+])
                 (saq-machine-eval :UNQ))

          p8 (-> stack
                 (saq-machine-eval "untouched")
                 (saq-machine-eval :UNQ))

          p9 (-> stack
                 (saq-machine-eval 5)
                 (saq-machine-eval :UNQ))

          p10 (-> stack
                 (saq-machine-eval :xyz)
                 (saq-machine-eval :UNQ))

          p11 (-> stack
                  (saq-machine-eval [])
                  (saq-machine-eval 1)
                  (saq-machine-eval :CONS))

          p11e (-> stack
                  (saq-machine-eval 1)
                  (saq-machine-eval [])
                  (saq-machine-eval :CONS))




          ]

      (is (= (.peek p1) 3) )
      (is (= (.peek p2) 2) )
      (is (= (.popn p3 2) [[:b :a],stack]) )

      (is (= (.peek p6) 3))
      (is (= (.peek p7) 7))
      (is (= (.peek p8) "untouched"))
      (is (= (.peek p9) 5))
      (is (= (.peek p10) :xyz))
      (is (= (.peek p11) [1]))
      (is (= (.peek p11e) "Tried to cons [] onto non-collection 1"))



      ) ))


(deftest saq-machine-cond
  (testing "SaqMachine conditional combinator"
    (let [stack (->SaqStack [])
          ;; Conditional works like this ...
          ;; if condition is a truth expression then its evaluated immediately (before the :cond)
          ;; if the condition is a [block] which takes values off the stack .. all those stack values need to be on the stack
          ;; BEFORE the three blocks of cond / yes / no

          p1 (-> stack
                 (saq-machine-eval "yes")
                 (saq-machine-eval "no")
                 (saq-machine-eval true)
                 (saq-machine-eval :COND))

          p2 (-> stack
                 (saq-machine-eval "yes")
                 (saq-machine-eval "no")
                 (saq-machine-eval false)
                 (saq-machine-eval :COND))


          p3 (-> stack
                  (saq-machine-eval "bottom1")
                  (saq-machine-eval "bottom2")
                  (saq-machine-eval [:DROP])
                  (saq-machine-eval [:=])
                  (saq-machine-eval 1)
                  (saq-machine-eval 2)
                  (saq-machine-eval :<)
                  (saq-machine-eval :COND))

          p4 (-> stack
                  (saq-machine-eval "bottom1")
                  (saq-machine-eval "bottom2")
                  (saq-machine-eval 1)
                  (saq-machine-eval 2)
                    (saq-machine-eval [:DROP])
                  (saq-machine-eval [:=])
                  (saq-machine-eval [:<])
                  (saq-machine-eval :COND))

          p5 (-> stack
                  (saq-machine-eval 0)
                  (saq-machine-eval "AAA")
                  (saq-machine-eval 1)
                  (saq-machine-eval 2)
                  (saq-machine-eval [:DROP])
                  (saq-machine-eval "BBB")
                  (saq-machine-eval [:>])
                  (saq-machine-eval :COND))

          p6 (-> stack
                  (saq-machine-eval 0)
                  (saq-machine-eval "AAA")
                  (saq-machine-eval 1)
                  (saq-machine-eval 2)
                  (saq-machine-eval [:DROP])
                  (saq-machine-eval "BBB")
                  (saq-machine-eval [:<])
                  (saq-machine-eval :COND))


          ]
      (is (= (.peek p1) "yes"))
      (is (= (.peek p2) "no"))
      (is (= (.peek p3) "bottom1"))
      (is (= (.peek p4) "bottom1"))
      (is (= (.peek p5) "BBB"))
      (is (= (.peek p6) 0))

      )    ))

(deftest saqMachine-rep
  (testing "SaqMachine Rep"
    (let [stack (->SaqStack [])
          p1 (-> stack
                 (saq-machine-eval 0)
                 (saq-machine-eval [1 :+ ])
                 (saq-machine-eval 10)
                 (saq-machine-eval :REP))]
      (is (= (.peek p1) 10))
      )))

(deftest saqMachine-templates
  (testing "saqMachine Strings and Templates"
    (let [stack (->SaqStack [])
          ;; Strings
          p1 (-> stack
                 (saq-machine-eval ":a :b :c")
                 (saq-machine-eval :EXPL) )
          p1a (-> p1 (saq-machine-eval :UNQ))

          p2 (-> stack
                 (saq-machine-eval "4 7 :+")
                 (saq-machine-eval :EXPL) )
          p2a (-> p2 (saq-machine-eval :UNQ))

          p3 (-> stack
                 (saq-machine-eval "world")
                 (saq-machine-eval "hello %s")
                 (saq-machine-eval :STPL))

          p4 (-> stack
                 (saq-machine-eval "teenage")
                 (saq-machine-eval "america")
                 (saq-machine-eval "hello %s %s")
                 (saq-machine-eval :DUP)
                 (saq-machine-eval :STPLCNT))

          p5 (-> p4
                 (saq-machine-eval :DROP)
                 (saq-machine-eval :STPL))

          ;; Other templates
          p6 (-> stack
                 (saq-machine-eval 2)
                 (saq-machine-eval [2 :$$ :+])
                 (saq-machine-eval :DUP)
                 (saq-machine-eval :TPLCNT) )
          p7 (-> p6
                 (saq-machine-eval :DROP)
                 (saq-machine-eval :TPL))

          p8 (-> stack
                 (saq-machine-eval 0)
                 (saq-machine-eval 2)
                 (saq-machine-eval [[:$$ :+] 5 :REP])
                 (saq-machine-eval :TPL))

          ;;p9 (-> p8 (saq-machine-eval :UNQ))

          ]
      (is (= (.len p1) 1))
      (is (= (.len p1a) 3))
      (is (= (.peek p1) [:a :b :c]))
      (is (= (.peek p1a) :c))
      (is (= (.peek p2) [4 7 :+]))
      (is (= (.peek p2a) 11))

      (is (= (.peek p3) "hello world"))
      (is (= (.peek p4) 2))
      (is (= (.peek p5) "hello teenage america"))
      (is (= (.peek p6) 1))

      (is (= (.peek p7) [2 2 :+]))
      (is (= (.peek p8) [[2 :+] 5 :REP]))
      )))

(deftest full-SaqMachine
  (testing "The full SaqMachine including channels"
    (is (= (take 10 (preparse [:A :B :C])) '(:A :B :C :A :B :C :A :B :C :A) ))
    (is (= (take 10 (preparse [:A :/// :B])) '(:A :B :B :B :B  :B :B :B :B :B) ))
    (let [integers  (fn [c] (pump (iterate (partial + 1) 0) c))
          taker (fn [c] (collector c 10))  ]

      ;; A pass through machine
      (is (= (let [cix (chan) co (chan)
                   in (integers cix)
                    filt (make-SaqMachine [:X :->] {:X cix} {:-> co})
                   result (collector co 10)]
               result)
           [0 1 2 3 4 5 6 7 8 9]))

      ;; Testing the "initialize :/// loop structure"
      (is (= (let [cix (chan) co (chan)
                   filt (make-SaqMachine ["a" :-> :/// "b" :->] {} {:-> co})
                   result (collector co 10)]
               result)
             ["a" "b" "b" "b" "b"  "b" "b" "b" "b" "b"]))

      ;; Simple machine to produce a counting sequence
      (is (= (let [c (chan)
                   gen (make-SaqMachine [0 :/// :DUP :-> 2 :+ ] {} {:-> c})
                   result (collector c 10)]
               result)
             [0 2 4 6 8 10 12 14 16 18 ]))

      ;; Simple machine to filter evens
      (is (= (let [ci (chan)
                   co (chan)
                   gen (make-SaqMachine [0 :/// :DUP :-> 1 :+ ] {} {:-> ci})
                   filt (make-SaqMachine [:X :DUP [:->] :SWAP 2 :% 0 :=   [:DROP] :SWAP  :COND ]  {:X ci} {:-> co})
                   result (collector co 10) ]
               result)
             [0 2 4 6 8 10 12 14 16 18]))

      ;; Testing the Linrec combinator to produce a sequence via a recursive call
      ;; :LINREC args [base-case test] [base-case block] [inward block] [outward block]
      (is (= (let [c (chan)
                   gen (make-SaqMachine [5 [:DUP 0 :=] [0] [1 :-] [:DUP :-> 1 :+] :LINREC] {} {:-> c} )
                   result (collector c 10)]
               result)
             [0 1 2 3 4 0 1 2 3 4]))
      ) ) )

(deftest future-blocks
  (testing "Future Blocks"
    (let [stack (->SaqStack [])

          f1 (-> stack
                 (saq-machine-eval [])
                 (saq-machine-eval [:+])
                 (saq-machine-eval 2)
                 (saq-machine-eval :FUT)
                 (saq-machine-eval 3)
                 (saq-machine-eval 5))

          ;; template in a future block
          f2 (-> stack
                 (saq-machine-eval [])
                 (saq-machine-eval [[:$$ :$$ :+] :TPL] )
                 (saq-machine-eval 2)
                 (saq-machine-eval :FUT)
                 (saq-machine-eval 7)
                 (saq-machine-eval 9)
                 (saq-machine-eval :UNQ))
          ]

      (is (= (.peek f1) 8))
      (is (= (.peek f2) 16))  )


    (is (= (let [c (chan)
                 f (make-SaqMachine [[1] [:+] 1 :FUT 2 :->] {} {:-> c} )
                 result (collector c 2) ]
             result)
           [3 3]) )

    (is (= (let [c (chan)
                 f (make-SaqMachine [[] [:+] 2 :FUT 1 4 :->] {} {:-> c} )
                 result (collector c 2) ]
             result)
           [5 5]) )

    (is (= (let [c (chan)
                 f (make-SaqMachine [[ ["hello %s %s" :STPL ] 2 :FUT] :UNQ "teenage" "america" :->] {} {:-> c})
                 result  (collector c 2)]
             result)
           ["hello teenage america" "hello teenage america"]))


    (is (= (let [ci1 (chan)
                 ci2 (chan)
                 co (chan)
                 m1 (make-SaqMachine [[] [["Hello %s" :STPL] 1 :FUT] :->] {} {:-> ci1})
                 m2 (make-SaqMachine ["World" :->] {} {:-> ci2})
                 f (make-SaqMachine [:X :UNQ :Y :->] {:X ci1 :Y ci2} {:-> co})
                 result (collector co 2)
                 ]
             result)
           ["Hello World" "Hello World"]))
    ))

(deftest evaluator
  (testing "evaluator"
    (let [in (symbol "in")
          out (symbol "out")
          s1 ""
          s2 "! in out"
          s3 "! out
# one node, produces stream of integers
{} {:-> out}
0 :/// :DUP :-> 1 :+
"
          s4 "! out
= c1
# first node, produces stream of integers
{} {:-> c1}
0 :/// :DUP :-> 1 :+
# second node, multiplies each pair
{:X c1 } {:-> out}
:X :X :* :DUP :*  :->"  ]
      (is (= (execute s1 {}) {:errors ["Empty Program"]}))
      (is (= (execute s2 {}) {:errors ["No required channel \"in\" given" "No required channel \"out\" given"]}))
      (let [c1 {in (chan) out (chan)}]
        (is (= (execute s2 c1) {:errors ["No nodes defined"]})))
      (is (= (-> (execute s3 {out (chan)}) :errors) [] ))
      (is (= (-> (execute s3 {out (chan)}) :chans (count)) 2 ))
      (is (= (-> (execute s4 {out (chan)}) :chans  keys) [(symbol  "out") (symbol  "c1")]))
      ))  )
