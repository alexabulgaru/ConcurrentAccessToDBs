## Tema 1b APD - Alexandra Bulgaru 331CD

### Observatii teste:
- Cresterea numarului de fire poate sa imbunatateasca performanta pana la un moment dat, dar apoi pot sa apara probleme
de supraincarcare a sistemului. De asemenea, datele de dimensiuni mari maresc timpul de procesare si consuma destul de
multe resurse (este foarte posibil ca la mai multe rulari consecutive, pe un laptop cu performanta decenta, testele sa
inceapa sa pice).
- Avand in vedere dimensiunea datelor anumitor teste, este esential ca implementarea sa fie cat mai optima, iar aici am
intampinat problema ca testele sa treaca, dar dupa 30 de secunde de exemplu (pentru un test cu timeout de 10 secunde). Listele nu erau suficient de eficiente, asa
am ajuns sa fac putin research si sa folosesc ConcurrentHashMap.
- Blocurile mai mari pot sa reduca numarul de operatii de sincronizare, insa pot si creste latenta sarcinilor. Blocurile
mai mici, desi pot sa permita o mai buna distributie a sarcinilor intre fire, pot si sa creasca overhead-ul de gestionare
a blocarilor.
- Cand avem un raport ridicat de scriitori, apare o crestere a timpilor de executie, in special cand avem un writer preferred
lock.
- Cand avem un storage de dimensiune mai mare, scade probabilitatea mai multor sarcini de a accesa acelasi index. Astfel,
scade competitia si imbunatateste performanta generala.
- O stocare mai mare poate sa gestioneze un volum mai mare de sarcini, fara a genera o competitie majora pentru resursele
partajate.

### Performanta
- ConcurrentHashMap permite accesul concurent fara alte blocari globale. Astfel, creste performanta cand avem multe operatii
de scriere/citire. In plus, este o structura de date scalabila, si avand in vedere dimensiunile testelor, era necesar sa
pot gestiona un numar mare de indecsi fara avea performanta afectata semnificativ.
- Am implementat ThreadPool folosindu-ma de un array de PoolWorker si o lista sincrona pentru coada de sarcini.
Synchronized ne asigura un acces thread-safe, dar aduce si un overhead semnificativ.
- In prioritizarea cititorilor, in cazurile cu multi cititori, se poate ca scriitorii sa fie blocati permanent, aparand
latente si posibile blocaje. 
- In priozitizarea scriitorilor (metoda 1) printr-un contor de scriere, se reduce ristul de starvation al acestora.
Cititorii pot fi blocati mai frecvent, aparand intarzieri in cazurile cu un numar mare de scriitori.
- In priozitizarea scriitorilor (metoda 2) folosesc ReentrantLock pentru o gestionare mai echilibrata. Conditiile permit
un control mai bun asupra fluxului de executie si reduc riscul de starvation a unui tip de operatie.
