(ns intools.dkmsin.actions)

(defn autoinstall []
  [:sh ["sudo" "dkms" "autoinstall"]])

(defn add [value]
  [:sh ["sudo" "dkms" "add" value]])
