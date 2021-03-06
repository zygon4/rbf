(ns rbf.core
  (:use clojure.repl
	clojure.java.javadoc
	[incanter.core :only [matrix mmult solve matrix-map view]]
	[incanter.charts :only [xy-plot line-chart]])
  (:require [clojure.math.numeric-tower :as math]))

;;; So the RBF  approximation is f(x) = sum (wi * k(d(x, xi)))

;;; The idea is to take all of the sample data points and construct a kernel function
;;; matrix using the distance between all points.
;;; Take this matrix multiplied by the weights and set equal to the samples true values.
;;; Solve (or approximate) the values of the weights.
;;; Then, with new data points, sum all of the sample distances against the new data point,
;;; multiplied by the sample weights.


;;; Here are some of the kernel functions
(def epsilon 0.01)

(defn gaussian [r]
  (math/expt Math/E (math/expt (* -1 (* epsilon r)) 2)))

(defn- quad-fn [r]
  (+ 1 (math/expt (* epsilon r) 2)))

(defn multiquadratic [r]
  (math/sqrt (quad-fn r)))

(defn inverse-quadratic [r]
  (/ 1 (quad-fn r)))

(defn inverse-multiquadratic [r]
  (/ 1 (math/sqrt (quad-fn r))))

; Collection of infinitely smooth RBFs
(def radial-fns [gaussian multiquadratic inverse-quadratic inverse-multiquadratic])


;;; Here is the distance function

;;;     First, Sir Peter Norvig esquire's impl
;;;(defun distance (point1 point2)
;;;  "The Euclidean distance between two points.
;;;  The points are coordinates in n-dimensional space."
;;;  (sqrt (reduce #'+ (mapcar #'(lambda (a b) (expt (- a b) 2))
;;;                            point1 point2))))

;;; Now my version
(defn- dis [x y]
  (math/sqrt (reduce + (map (fn [a b] (math/expt (- a b) 2)) x y))))


;;; Lets construct a distance matrix of all the samples
(defn d-matrix [samples]
  (matrix (for [s1 samples s2 samples] (dis s1 s2)) (count samples)))

;;; Now we need to construct a matrix of kernel functions given
;;; all the samples
(defn k-matrix [d-matrix kernel]
  (matrix (matrix-map kernel d-matrix)))


;;; Create a result matrix
(defn result-matrix [results]
  (matrix results))


;;; learn the weights associated with the data and result matricies
(defn calc-weights [data results]
  (solve data results))


;;; Finally, create a general function to return an approximation
;;; to a new data point.
(defn approx [data-points results kernel]
  (let [d (d-matrix data-points) ;; distance matrix
	k (k-matrix d kernel)    ;; kernel matrix
	r (result-matrix results);; result matrix
	w (calc-weights d r)     ;; weight matrix
	]
    (fn [point]
      ;;; need to multiply (distances between the new point and
      ;;; all the samples) * sample weights
      (let [distances (map dis data-points (repeat point))]
	(comment (println distances))
	(reduce + (map * w distances))))))

;;; Now, we need to calculate our regression error
(defn error [result expected]
  (- result expected))


;;; Some testing code
(defn sine-samples
  ([n] (take n (map (fn [x] (vector x (Math/sin x))) (repeatedly rand))))
  ([n even-spaced?] (take n (map (fn [x] (vector x (Math/sin x))) (range 0 1 (/ 1.0 n))))))


(defn sine-err-data [train-sample-min train-sample-max test-sample-count]
  (map
   (fn [x]
     (let [train-data (sine-samples x true)
	   test-data  (sine-samples test-sample-count)
	   rbf-approx (approx train-data (map last train-data) inverse-multiquadratic)
	   results (map rbf-approx test-data)
	   errors (map error results (map last test-data))
	   total-error (reduce + errors)]
       [x total-error]))
   (range train-sample-min train-sample-max)))

;;; A parameter set for the work function
(def params [[25 50 500]
	     [25 100 500]
	     [25 200 500]])

;;; This just takes a vector of triples and runs the RBF over them, plotting the results
;;; the axis labling doesn't seem to work right now... boo
(defn work [params]
  (doseq [[train-min train-max test-count] (lazy-seq params)]
    (time
     (let [err-data (sine-err-data train-min train-max test-count)
	   x-data (map first err-data)
	   y-data (map last err-data)]
       (view (xy-plot x-data y-data :x-label "training samples" :y-label "error sum"))))))
