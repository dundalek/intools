(ns intools.randrin.app.db)

(def default-db {:state {:selected 0}
                 :items (range 10)
                 :display-list-state {:selected 0}
                 :screen nil
                 :name "World"
                 ;; TODO dynamic registration of focusables
                 :focus-manager {:focusables [:action-menu :display-panel]
                                 :active-id nil}
                 :terminal-size nil
                 :component-state {}})
