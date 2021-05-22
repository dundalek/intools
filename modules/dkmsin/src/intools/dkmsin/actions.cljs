(ns intools.dkmsin.actions)

(defn format-kernel [{:keys [kernel-version arch]}]
  (str kernel-version "/" arch))

(defn autoinstall []
  [:sh ["sudo" "dkms" "autoinstall"]])

(defn match [source-kernel target-kernel]
  [:sh ["sudo" "dkms" "match" "--templatekernel" (format-kernel source-kernel) "-k" (format-kernel target-kernel)]])

