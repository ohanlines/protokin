(ns protokin.security.crypto
  (:require
    [clojure.edn :as edn])
  (:import
    (java.nio.charset StandardCharsets)
    (java.security SecureRandom)
    (javax.crypto Cipher SecretKeyFactory)
    (javax.crypto.spec GCMParameterSpec PBEKeySpec SecretKeySpec)))

(def default-kdf
  {:algo "pbkdf2-sha256"
   :iterations 210000
   :key-length 32})

(defn random-bytes
  [n]
  (let [bytes (byte-array n)
        rng (SecureRandom.)]
    (.nextBytes rng bytes)
    bytes))

(defn derive-kek
  [passphrase salt {:keys [iterations key-length]}]
  (let [factory (SecretKeyFactory/getInstance "PBKDF2WithHmacSHA256")
        spec (PBEKeySpec. (.toCharArray (str passphrase))
                          ^bytes salt
                          (int iterations)
                          (* 8 (int key-length)))
        key (.generateSecret factory spec)]
    (.getEncoded key)))

(defn generate-dek
  []
  (random-bytes 32))

(defn- encrypt-bytes
  [key-bytes plaintext-bytes aad-bytes]
  (let [nonce (random-bytes 12)
        cipher (Cipher/getInstance "AES/GCM/NoPadding")
        key (SecretKeySpec. ^bytes key-bytes "AES")
        gcm-spec (GCMParameterSpec. 128 ^bytes nonce)]
    (.init cipher Cipher/ENCRYPT_MODE key gcm-spec)
    (when aad-bytes
      (.updateAAD cipher ^bytes aad-bytes))
    {:ciphertext (.doFinal cipher ^bytes plaintext-bytes)
     :nonce nonce
     :aad aad-bytes}))

(defn- decrypt-bytes
  [key-bytes ciphertext-bytes nonce aad-bytes]
  (let [cipher (Cipher/getInstance "AES/GCM/NoPadding")
        key (SecretKeySpec. ^bytes key-bytes "AES")
        gcm-spec (GCMParameterSpec. 128 ^bytes nonce)]
    (.init cipher Cipher/DECRYPT_MODE key gcm-spec)
    (when aad-bytes
      (.updateAAD cipher ^bytes aad-bytes))
    (.doFinal cipher ^bytes ciphertext-bytes)))

(defn wrap-dek
  [kek dek aad-bytes]
  (encrypt-bytes kek dek aad-bytes))

(defn unwrap-dek
  [kek wrapped-dek dek-wrap-nonce aad-bytes]
  (decrypt-bytes kek wrapped-dek dek-wrap-nonce aad-bytes))

(defn encrypt-expense-payload
  [dek payload aad-bytes]
  (let [plaintext (.getBytes (pr-str payload) StandardCharsets/UTF_8)]
    (encrypt-bytes dek plaintext aad-bytes)))

(defn decrypt-expense-payload
  [dek ciphertext nonce aad-bytes]
  (let [plaintext (decrypt-bytes dek ciphertext nonce aad-bytes)
        payload-text (String. ^bytes plaintext StandardCharsets/UTF_8)]
    (edn/read-string payload-text)))
