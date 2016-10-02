(ns qasac.saq)

(defprotocol ISaqStack
  (len [this])
  (push [this x] )
  (pushn [this xs])
  (pop [this])
  (popn [this n])
  (peek [this])

  (dup [this])
  (swap [this])
  (drop [this])
  (empty? [this])
  (nip [this])
  (tuck [this])
  )

(defrecord SaqStack [xs]
  ISaqStack
  (len [{:keys [xs]}] (count xs))
  (empty? [this] (= 0 (.len this)))
  (push [this x] (->SaqStack (cons x xs)))
  (pushn [this xs]  (reduce push this xs) )

  (pop [{:keys [xs] :as this}] (list (peek this) (->SaqStack (rest xs))))
  (popn [this n]
    (loop [s this counter n ys '()]
      (if (= counter 0) (list ys s)
          (let [[val new-stack] (.pop s)]
            (recur new-stack (- counter 1) (conj ys val))
            )) ) )
  (peek [{:keys [xs]}] (first xs))

  (dup [this] (push this (peek this))  )

  (swap [this]
    (let [[ [x1 x2] s] (popn this 2)]
      (pushn s [x2 x1])  ))

  (drop [this] (-> this (pop) (nth 1)))

  (nip [this] (let [[top s] (.pop this)] (-> s (.drop) (.push top)) ) )
  (tuck [this] (let [[x1 s1] (.pop this)
                     [x2 s2] (.pop s1)]
                 (-> s2 (.push x1) (.push x2) (.push x1) )) )
  )
