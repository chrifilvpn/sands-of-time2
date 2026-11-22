# Risolutore (viaggi minimi per la modalità impossibile)

`Risolutore.java` rifà la fisica del gioco fotogramma per fotogramma (stessi numeri di `Esploratore` e `LevelScreen`)
e cerca il numero minimo di viaggi nel tempo per finire un livello prendendo tutti gli scarabei.
Il risultato va nelle proprietà `viaggiPresente` / `viaggiPassato` della mappa del presente.

    javac Risolutore.java
    DT=60 ASSESTA=120 ORDINE=misto QARIA=6 java -Xmx6g Risolutore ../assets 5 0 4 0.5 0.5 40

(livello 5, partendo nel presente; `1` al posto di `0` = partendo nel passato).
Non usa libGDX: serve solo Java. Se cambi un livello, ricalcola i suoi numeri.
