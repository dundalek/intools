(ns lshwin.core-test
  (:require
   [lazytest.core :refer [defdescribe it expect]]
   [lshwin.core :as sut]))

(defdescribe expand-bridge-children
  (it "expands bridge children in place"
      (let [data {:id "root" :class "system"
                  :children [{:id "bridge1" :class "bridge"
                              :children [{:id "child1" :class "memory"}
                                         {:id "child2" :class "processor"}]}
                             {:id "disk1" :class "disk"}]}
            expected {:id "root" :class "system"
                      :children [{:id "child1" :class "memory"}
                                 {:id "child2" :class "processor"}
                                 {:id "disk1" :class "disk"}]}]
        (expect (= expected (sut/expand-bridge-children data)))))

  (it "handles nested bridges recursively"
      (let [data {:id "root" :class "system"
                  :children [{:id "bridge1" :class "bridge"
                              :children [{:id "bridge2" :class "bridge"
                                          :children [{:id "memory1" :class "memory"}]}
                                         {:id "processor1" :class "processor"}]}]}
            expected {:id "root" :class "system"
                      :children [{:id "memory1" :class "memory"}
                                 {:id "processor1" :class "processor"}]}]
        (expect (= expected (sut/expand-bridge-children data)))))

  (it "preserves non-bridge items with children"
      (let [data {:id "root" :class "system"
                  :children [{:id "bus1" :class "bus"
                              :children [{:id "memory1" :class "memory"}]}
                             {:id "bridge1" :class "bridge"
                              :children [{:id "processor1" :class "processor"}]}]}
            expected {:id "root" :class "system"
                      :children [{:id "bus1" :class "bus"
                                  :children [{:id "memory1" :class "memory"}]}
                                 {:id "processor1" :class "processor"}]}]
        (expect (= expected (sut/expand-bridge-children data)))))

  (it "handles bridge with no children"
      (let [data {:id "root" :class "system"
                  :children [{:id "bridge1" :class "bridge"}]}
            expected {:id "root" :class "system"
                      :children []}]
        (expect (= expected (sut/expand-bridge-children data)))))

  (it "handles bridge with empty children vector"
      (let [data {:id "root" :class "system"
                  :children [{:id "bridge1" :class "bridge" :children []}]}
            expected {:id "root" :class "system"
                      :children []}]
        (expect (= expected (sut/expand-bridge-children data)))))

  (it "handles multiple bridges at same level"
      (let [data {:id "root" :class "system"
                  :children [{:id "bridge1" :class "bridge"
                              :children [{:id "memory1" :class "memory"}]}
                             {:id "bridge2" :class "bridge"
                              :children [{:id "processor1" :class "processor"}]}]}
            expected {:id "root" :class "system"
                      :children [{:id "memory1" :class "memory"}
                                 {:id "processor1" :class "processor"}]}]
        (expect (= expected (sut/expand-bridge-children data))))))

(defdescribe flatten-children
  (it "flattens nested children into a single sequence"
      (let [data {:id "root" :class "system"
                  :children [{:id "child1" :class "memory"
                              :children [{:id "grandchild1" :class "bank"}]}
                             {:id "child2" :class "processor"}]}
            expected [{:id "root" :class "system"
                       :children [{:id "child1" :class "memory"
                                   :children [{:id "grandchild1" :class "bank"}]}
                                  {:id "child2" :class "processor"}]}
                      {:id "child1" :class "memory"
                       :children [{:id "grandchild1" :class "bank"}]}
                      {:id "grandchild1" :class "bank"}
                      {:id "child2" :class "processor"}]]
        (expect (= expected (sut/flatten-children data)))))

  (it "handles item with no children"
      (let [data {:id "leaf" :class "memory"}
            expected [{:id "leaf" :class "memory"}]]
        (expect (= expected (sut/flatten-children data)))))

  (it "handles item with empty children"
      (let [data {:id "root" :class "system" :children []}
            expected [{:id "root" :class "system" :children []}]]
        (expect (= expected (sut/flatten-children data))))))
