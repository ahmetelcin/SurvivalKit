# SurvivalKit — Kullanım Kılavuzu

Minecraft 26.3 için istemci taraflı Fabric yaşam kalitesi modu.

---

## 1. Kurulum

1. **Gereksinimler:**
   - Minecraft 26.3
   - [Fabric Loader](https://fabricmc.net/) (0.19.5 veya üzeri)
   - Java 25
   - [Fabric API](https://modrinth.com/mod/fabric-api)
2. **Yükleme:**
   - Derlenmiş `survivalkit-<sürüm>.jar` dosyasını Minecraft dizinindeki `.minecraft/mods/` klasörüne kopyalayın.
   - Minecraft 26.2 için [0.12.1](https://github.com/ahmetelcin/SurvivalKit/releases/tag/v0.12.1-mc26.2) sürümünü kullanın (Fabric Loader 0.19.3 veya üzeri). 0.12.1 sonrası değişiklikler onda yok.

---

## 2. Menüyü Açma ve Temel Kontroller

- **Menü Tuşu:** Varsayılan tuş **Sağ Shift** (`Right Shift`).
- **Tuşu Değiştirme:** Minecraft seçeneklerinden *Seçenekler → Kontroller → Tuş Atamaları* altındaki **SurvivalKit** kategorisinden (`key.survivalkit.menu`) istenen tuşa atanabilir.
- **Menü Düzeni:**
  - **Sol Panel:** Modül listesi, arama kutusu ve modül açma/kapatma düğmeleri.
  - **Sağ Panel:** Seçili modülün ayarları, tuş ataması ve katlanabilir ayar grupları.
  - **Arama Kutusu:** Modül adları veya ayar adları Türkçe karakter duyarsız olarak aranabilir.
  - **Üst çubuk:** Solda profil seçici ve profil düğmeleri, sağda **HUD'u düzenle** (bkz. bölüm 4 ve HUD).
  - **Hazır ayarlar:** Her modülün ayarlarının başında; bkz. bölüm 4.
  - **↺:** Değeri değişmiş bir ayarın sağında çıkar; tıklayınca varsayılana döner. Bir hazır ayarın değiştirdiği ayarda önce hazır ayardan önceki değerine döner.

---

## 3. Modüller ve Ayarlar

### Otomatik Yeme (`autoeat`)
*Açlık eşiğin altına düşünce kendiliğinden yer ve hotbar'ı bulduğu gibi bırakır.*

- **Ortak Ayarlar:**
  - **Açık:** Modülün açık olup olmadığı; profille birlikte kaydedilir.
  - **Tuş:** Bu modülü açıp kapatan tuş.
- **Tetikleme:**
  - **Eşik modu:** Sabit veya Otomatik.
    - *Sabit:* Açlık, açlık eşiğinin altına düşünce yer.
    - *Otomatik:* Seçilecek yiyeceğin dolduracağı kadar açlık boşalınca yer, hiçbir puan boşa gitmez: biftek (8) 12'de, ekmek (5) 15'te.
  - **Açlık eşiği:** Açlık bu değerin altına düşünce yemeye başlar (20 tam dolu demek).
  - **İyileşmek için erken ye:** Can burada ayarlanan değerin altındayken, açlık tam dolu olmadığı sürece açlığı beklemeden yer; böylece doğal can yenilenmesi hiç durmaz. Yalnızca sıradan yiyecek yenir: altın elma, altın elma kuralının işidir ve onun eşiği tutuyorsa önce o çalışır.
  - **Erken yeme can eşiği:** Can bu değerin altındayken açlığı beklemeden yer (20 tam dolu demek).
- **Yiyecek:**
  - **Tercih sırası:** Önce yenecek yiyecekler, en baştaki en makbulü. Listede olan, olmayana her zaman tercih edilir.
  - **Kara liste:** Hiçbir koşulda yenmeyecek yiyecekler. Zarar verenler ve koro meyvesi ekli gelir; istediğini çıkarabilirsin.
  - **Listede hiçbiri yoksa:** Tercih sırasındaki yiyeceklerin hiçbiri envanterde yoksa geri kalanlardan hangisinin yeneceği (*En doyurucu*, *En uzun dayanan*, *Önce ucuz olan*, *Hiçbir şey yeme*); zararlı yiyecekler her zaman en sona kalır.
- **Altın Elma:**
  - **Can düşünce kullan:** Can azalınca açlıktan bağımsız olarak altın elma ye. Altın elmalar açlık için asla kendiliğinden seçilmez; yalnızca bu kuralla ya da tercih sırasına koyarsan yenir.
  - **Büyülüleri de harca:** Can kuralı büyülü altın elmayı da harcayabilsin. Kapalıyken yalnızca sıradan altın elma kullanılır.
  - **Can eşiği:** Can bu değerin altına düşünce altın elma çıkar (20 tam dolu demek).
- **El ve Envanter:**
  - **Arama kapsamı:** Yiyeceğin nerede aranacağı (*Hotbar* veya *Tüm envanter*). Tüm envanter seçilirse sırt çantasındaki yiyecek elindeki slota takaslanır, yemek bitince geri konur.
  - **Eski slota dön:** Yemek bitince yemeden önce seçili olan hotbar slotuna geri dön.
  - **Yeme eli:** Yemeğin hangi elde yeneceği (*Ana el* veya *Diğer el*). Diğer el seçilirse yiyecek geçici olarak diğer ele alınır, ana eldeki kılıç ya da blok hiç yerinden oynamaz; yemek bitince diğer elin eski eşyası geri konur.
  - **Diğer eldeki totemi çıkarma:** Diğer elde ölümsüzlük totemi varsa o sefer ana elde yenir, totem yerinden oynamaz.

---

### Otomatik Totem (`autototem`)
*Diğer elinde ölümsüzlük totemi bulundurur; totem patlayınca yenisini aynı ele koyar.*

- **Tetikleme:**
  - **Ne zaman:** Totemin ne zaman diğer elde tutulacağı.
    - *Her zaman:* O el kalıcı olarak toteme ayrılır.
    - *Can eşiği:* El yalnızca can düşünce ödünç alınır.
  - **Can eşiği:** Can bu değerin altına düşünce totem diğer ele girer (20 tam can demek).
  - **Can gelince geri koy:** Can eşiğin üstüne çıkınca diğer elde ne varsa geri koy. Kapalıysa totem elde kalır.
- **Totem Bitince:**
  - **Yerine tut:** Hiç totem kalmadığında diğer ele konacak eşyalar, sıra önemli: envanterde bulunan ilki girer. Boş bırakılırsa el boş kalır. Totem bulunur bulunmaz listedeki eşyanın yerini alır.
  - **Uyar:** Totem gerektiği anda hiç totem kalmadıysa sohbete tek satır uyarı yazar (*"Totem bitti — diğer el totemsiz."*). Yeniden totem bulununca uyarı tekrar hazırlanır.
- **El ve Envanter:**
  - **Arama kapsamı:** Totemin nerede aranacağı (*Hotbar* veya *Tüm envanter*). Tüm envanter seçilirse sırt çantasındaki totem de kullanılır.

---

### Otomatik Alet (`autotool`)
*Blok kırmaya başlayınca en iyi aleti ele alır, bırakınca elindekini geri koyar.*

- **Hangi Alet:**
  - **Dayanıklılık eşiği:** Kalan kullanımı bunun altındaki alet hiç ele alınmaz, elinde kırılmasın diye. 0 aletin son kullanımına kadar kullanılmasına izin verir.
  - **Kılıcı elde bırak:** Elinde kılıç varken bir bloğa vurmak onu aletle değiştirmez — yanlışlıkla bir tık dövüşte silahını almaz.
  - **Eldeki İpeksi Dokunuşa dokunma:** Eldeki İpeksi Dokunuşlu alet, aleti olduğu bloklarda elde kalır — taşta kazma, çimende kürek — böylece bloğun kendisini alırsın. Başka bloklarda yine doğru alet çıkar.
  - **Sadece elde alet varken:** Yalnızca elinde zaten kazma, balta, kürek, çapa ya da makas varken geçiş yapar. Elin boşken, elinde yiyecek ya da blok varken bir bloğa vurmak elini olduğu gibi bırakır.
- **Büyüler:**
  - **İpeksi Dokunuşla kır:** Bu bloklar için daha hızlısı olsa da İpeksi Dokunuşlu alet alınır. Yine de bloğu düşürebilmesi gerekir.
  - **Servetle kır:** Bu bloklar için daha hızlısı olsa da en yüksek Servetli alet alınır. Yine de bloğu düşürebilmesi gerekir.
- **El ve Envanter:**
  - **Arama kapsamı:** Aletin nerede aranacağı (*Hotbar* veya *Tüm envanter*). Tüm envanter seçilirse sırt çantasındaki alet elindeki slota takaslanır, sonra geri konur.
  - **Eski eşyaya dön:** Saldırı tuşunu bırakınca ya da bir saniye hiçbir şey kırılmayınca elindekini geri koyar. Art arda kırılan bloklar tek geçiş, tek dönüştür. Kapalıysa alet elde kalır.

---

### Alet Seçici (`toolpicker`)
*Bir bloğa orta tıklayınca o blok için en iyi aleti ele alır. Otomatik Alet'in blok listelerini ve dayanıklılık eşiğini kullanır.*

- **Orta tık:** Bir bloğa orta tıklamanın ne yapacağı:
  - *Her zaman alet:* Bloğun kendisini hiç seçmez, her zaman en uygun aleti ele alır.
  - *Blok yoksa alet:* Blok envanterde varsa bloğu, yoksa en uygun aleti seçer.
  - *Kapalı:* Oyunun standart blok seçme (pick block) davranışını olduğu gibi bırakır.
  *(Not: Yaratıcı modda her zaman oyunun kendi blok seçmesi çalışır.)*
- **Arama kapsamı:** Aletin nerede aranacağı (*Hotbar* veya *Tüm envanter*).

---

### Dünya İndirici (`wdl`)
*Sunucuda gezdiğin chunk'ları diske yazar; sonra tek oyunculu olarak açabilirsin.*

- **Ayarlar:**
  - **Aynı dünyaya devam et:** Aynı sunucudan yapılan her indirme, adresinden adını alan tek bir dünyaya eklenir. Yeniden görülen chunk'lar güncellenir; bu sefer görülmeyenler olduğu gibi kalır, daha önce açılan sandıkların içi korunur. Kapalıyken her seferinde tarihli yeni bir dünya açılır.
  - **Seed:** Sunucuda `/seed` çalışıyorsa buraya yapıştır; boşsa 0 yazılır.
  - **Otomatik seed sorgusu:** Sunucuya bağlanınca yetki varsa `/seed` komutunu otomatik sor ve yanıtı seed ayarına yaz.
  - **Sahiplik:** Kaydedilen evcil varlıkların (köpek, kedi, at vb.) sahipliğinin nasıl ayarlanacağı:
    - *Olduğu gibi:* Orijinal sahiplik UUID'sini korur.
    - *Hepsi bana geçsin:* Yerel oyuncu UUID'ne aktarır.
  - **Kayıt aralığı (sn):** Yüklü chunk'ların diske kaydedilip boşaltılma aralığı (saniye).
  - **Kendi envanterim ve konumumla başla:** İndirilen dünyayı tek oyunculuda açtığında sunucudaki envanterin, ekipmanın, konumun, XP ve can durumunla başlarsın; kapalıysa varsayılan dünya başlangıcı kullanılır.
- **İstemci Komutları (`/wdl`):**
  - `/wdl start` — Dünya indirmeyi başlatır.
  - `/wdl stop` — Dünya indirmeyi durdurur ve kaydedilen chunk sayısını ve konumunu gösterir.
  - `/wdl status` — İndirmenin aktif olup olmadığını ve kaç chunk kaydedildiğini gösterir.
  - `/wdl save` — Bellekteki chunk'ları ve dünya durumunu anında diske kaydeder.
  - `/wdl help` — Komut kullanım bilgisini gösterir.
- **Sunucu İzinleri:**
  Sunucu indirmeyi yasaklarsa (`wdl:init` kanalı), indirme başlatılmaz ve sohbet ekranına bildirim verilir:
  *"Bu sunucu dünyanın indirilmesine izin vermiyor; hiçbir şey kaydedilmiyor."*

---

### Oto Silah (`autoweapon`)
*Elde silah varken nişangâhın üstündeki hedef için en iyi silahı çıkarır: ölümsüzlere Smite, örümceklere Bane of Arthropods, diğerlerine Keskinlik.*

- **Silahlar:**
  - **Kullanılacak:** Hangi silahların ele alınabileceği (*Kılıç*, *Balta*, *Kılıç ve balta*). Balta daha sert vurur ama daha yavaş toparlanır; varsayılan yalnız kılıç.
  - **Dayanıklılık eşiği:** Kalan kullanımı bunun altındaki silah hiç ele alınmaz, dövüşün ortasında kırılmasın diye (0 silahın son kullanımına kadar kullanılmasına izin verir).
- **Neye Göre:**
  - **Seçim kaynağı:** Eldeki silahın neye göre seçileceği (*Sadece nişan* veya *Nişan, yoksa çevredeki çoğunluk*; varsayılan ikincisi). Nişan alınan bir mob varsa her zaman o önce gelir. İkinci seçenekte nişan yoksa menzildeki düşman moblar en iyi silahlarına göre sayılır ve çoğunluğun silahı ele alınır; eşitlikte en yakın mob karar verir. Yeni çoğunluk en az 1 saniye sürmeden silah değişmez.
  - **Menzil:** Çoğunluk sayılırken ve hazırlık slotu doldurulurken kaç blok içindeki düşman mobların hesaba katılacağı (varsayılan 8). İnek, köylü gibi düşman olmayan moblar ve oyuncular hiç sayılmaz.
- **Nişanda:**
  - **Hedefler:** Kimin için silah seçileceği (*Moblar* veya *Moblar ve oyuncular*). Varsayılan yalnız moblar; diğer oyuncular yalnız sen seçersen.
  - **Otomatik vur:** Saldırı bekleme süresi dolar dolmaz nişangâhın üstündekine vurur, asla önce değil. Yalnız senin nişan aldığına; senin yerine nişan alınmaz.
- **Hazırlık Slotu:**
  - **Hangi slot:** Çevredeki düşman mobların çoğunluğu için en iyi silahın, sen nişan almadan önce envanterin herhangi bir yerinden getirilip konacağı hotbar slotu (1-9). 0 kapalı. Elindeki eşyayı hiç değiştirmez.
  - **Slotu da seç:** Hazırlanan slotu ele de al. Varsayılan kapalı: silah slotunda bekler, elin olduğu gibi kalır.
- **Dövüş Kilidi:**
  - **Süre (saniye):** Bir şeye vurduktan ya da hasar aldıktan sonra bu kadar süre, saldırı bekleme süresini sıfırlayacak hiçbir silah değişimi yapılmaz. Aynı eşyanın iki kopyası arasında geçiş (ör. iki demir kılıç) süreyi sıfırlamadığı için serbesttir; bekleme süresi zaten dolduysa da serbesttir. 0 kapalı.
  - **Nişanda da silahı tut:** Kilit nişandaki ve çevredeki çoğunluğa göre yapılan geçişleri de tutsun. Kapalıysa nişanda her zaman en iyi silah çıkar, bekleme süresi sıfırlansa bile.

---

### Damar Kırıcı (`veinminer`)
*Bütün bir ağacı ya da cevher damarını sunucunun kendi komutlarıyla bir seferde kırar (`/loot`, `/give`, `/fill`, `/setblock` — hangisine yetkin varsa). `/setblock` yetkin olmayan sunucuda hiçbir şey yapmaz.*

- **Tetik:**
  - **Kırma tuşu:** Modül açıkken işi başlatan tuş. Varsayılan atanmamış: sen seçene kadar hiçbir şey olmaz.
  - **Nasıl başlar:**
    - *Basılı tutup kır* (varsayılan): Tuşu basılı tutup bir kütüğü ya da cevheri normal kır, ağacın ya da damarın geri kalanı arkasından gider.
    - *Bakıp bas:* Bakıp tuşa bir kez bas; yarıda durdurmak için yine bas.
  - **Çerçeve göster:** Gidecek her bloğun çevresine çerçeve çizer. Çerçeve yoksa hiçbir şey gitmez — listede değil, doğal ağaç değil ya da `/setblock` yetkin yok.
- **Hedef:**
  - **Bloklar:** Ağacın ya da damarın nelerden oluşabileceği. Yalnız başladığın blokla aynı türdekiler izlenir; bir cevherle derin kayrak hâli aynı tür sayılır.
  - **En fazla:** Bir seferde bundan fazla blok gitmez (varsayılan 128); en yakındakiler önce, başladığın blok da sayılır.
  - **Sadece doğal ağaç:** Kütükler yalnız dünyanın büyüttüğü yapraklar onlara değiyorsa kırılır; böylece ağacın yanındaki kütük evine dokunulmaz. Elle konan yaprak sayılmaz.
- **Sunucu:**
  - **Eşyalar:** *Envantere* ya da *Bloğun yerine düşsün*. Sunucu `/loot`'a izin veriyorsa ana eldeki aletin Servet ve İpeksi Dokunuşu geçer. `/loot` yoksa: İpeksi Dokunuşla bloklar `/give` ile kendileri olarak gelir; cevherde Servet varsa eşyalar vanilla'nın kendi kurallarıyla hesaplanıp `/give` ile, XP'si yetkin varsa `/xp` ile gelir; ikisi de yoksa elle kırılmış gibi yerlerine düşer. Bilinen eksikler: aletin dayanıklılığı azalmaz, `/loot` XP vermez, envantere sığmayan eşya kaybolur.
  - **Saniyede en fazla komut:** Herhangi bir saniyede bundan fazla komut gitmez (varsayılan 10), eşit aralıklarla gider; sunucu spam sanıp atmasın diye.
  - **Başarı mesajlarını gizle:** Ağaç ya da damar kırılırken sunucunun bu komutlara verdiği yanıtlar sohbetine düşmez. Diğer operatörler yine görebilir.

- **Sınırlar:**
  - **Alan yoksa:** Yapı Modu alanı geçerli değilken (Yapı Modu kapalı ya da bu boyutta alan seçilmemiş) ne yapılacağı: *Her yerde çalış* (varsayılan) ya da *Hiç çalışma*. Alan geçerliyken yalnız içindeki bloklar gider; ağacın ya da damarın dışarı taşan kısmı olduğu gibi kalır.
  - **Kaç blokta bir açlık puanı:** Kırılan her bu kadar blok için 1 açlık puanı (yarım but) ödenir (varsayılan 10); artan bloklar sonraki işe sayılır, 0 bedava. Bedel sunucunun `/effect` komutuyla kısa bir Açlık etkisi olarak ödenir, önce doygunluktan düşer; `/effect` yetkin yoksa Damar Kırıcı çalışmaz. Açlık çubuğunu 7'nin altına (koşamayacağın yere) düşürecek iş hiç başlamaz, sohbete yazar. Barışçıl zorlukta açlık çubuğu düşmez, bedel yalnız doygunluktan gider.
  - **Alet aşınsın** (varsayılan açık): Ana eldeki alet her bloğu elle kırmışsın gibi aşınır — blok başına aletin hasarı (kazma ve baltada 1, kılıçta 2), Kırılmazlık oyundaki gibi bir kısmını bağışlar. Sunucunun `/item` komutuyla yazılır; `/item` yetkin yoksa aşınan bir alet eldeyken Damar Kırıcı çalışmaz. Alet asla kırılmaz: aleti tüketecek iş hiç başlamaz, sohbete yazar.

---

### Yapı Modu (`buildmode`)
*Bir değnekle alan seçer ve blok kıran modülleri (şimdilik Damar Kırıcı) o alanın içinde tutar. Alan dünyada çerçeveyle görünür, her sunucu ve boyut için ayrı saklanır; oyundan çıkıp girince yerinde durur, başka boyutta geçerli olmaz.*

- **Açık:** Açıkken değnek çalışır, alan çizilir ve alan kuralı geçerlidir. Kapalıyken değnek sıradan bir eşyadır, çerçeve gizlenir, alan hiçbir şeyi kısıtlamaz (ama saklanır).
- **Değnek:** Köşeleri seçen eşyalar (varsayılan tahta kılıç). Eldeyken bir bloğa sol tık 1. köşeyi, sağ tık 2. köşeyi seçer; ikisi de blok kırmaz, koymaz. Sohbete köşenin koordinatı, iki köşe seçilince alanın boyutu ve hacmi yazılır. Modül açıkken Oto Silah ve Otomatik Alet değneğe dokunmaz: elden almaz, hazırlık slotuna koymaz.
- **Komut:** `/bolge` ya da `/bolge goster` alanı sohbete yazar; `/bolge temizle` bu boyuttaki alanı siler; `/bolge kose1` ve `/bolge kose2` köşeyi bulunduğun bloğa koyar.

---

### İskele (`scaffold`)
*Yürürken bir sonraki adımının altı boşsa oraya blok koyar; boşluğun üstünden yürüyüp geçersin. Bakışın hiç döndürülmez: blok, yanındaki bloğa senin de yapacağın sağ tıklamayla, oyunun kendi yerleştirmesiyle konur. Hız, zıplama ya da sunucuya giden hareket bilgisi değiştirilmez.*

- **Yerleştirme:**
  - **Gecikme:** Blok koyduktan sonra kaç tick beklensin (0–20, varsayılan 0).
  - **Tick başına blok:** Bir tick'te en fazla kaç blok (1–4, varsayılan 1).
  - **Havayı / Suyu / Lavı doldur:** Hangi boşluklara blok konsun (varsayılan hava ve lav açık, su kapalı). Çimen, kar gibi bloğun yerini aldığı şeyler hava sayılır.
  - **Neyin yanına:** *Her blok* ya da *Sadece listedekiler* (+ liste). Liste köprünün nereden başlayacağını belirler: İskele'nin bu köprüde kendi koyduğu bloklar listede olmasa da dayanak sayılır, köprü kendi üstünde sürer. Liste boşsa kısıtlama yoktur, her bloğun yanına konur (listenin altında "Liste boş: her bloğun yanına konur" yazar). Yanında dayanacak blok varken liste hepsini reddederse HUD'da kırmızı "İskele: dayanacak blok listede yok". Kullanılınca kendi işini yapan bloklar (sandık, kapı, şalter, yatak…) hiçbir zaman destek sayılmaz.
- **İleri ve yukarı:**
  - **İleri uzat:** Yürürken bir sonraki adımın ötesine dümdüz bu kadar blok daha (0–5).
  - **Y seviyesini koru:** Köprü bitene kadar başladığı seviyede kalır; yolda bir merdivene çıksan da. Zıplamak köprüyü hiçbir durumda yükseltmez.
  - **Y kilidi:** *Kapalı* / *Başladığım yerin Y'si* (İskele'yi açtığında ayağının altındaki blok) / *Belirli Y* (aşağıdaki sayı). Açıkken her blok o Y'ye konur; zıplasan da, koşarak zıplasan da köprü düz kalır. Erişebildiğin sürece konur. Kilit açıkken kule yapılmaz. Kilitli Y eylem çubuğunda sayının yanında görünür.
  - **Belirli Y:** *Belirli Y* seçiliyken köprünün yüksekliği (−64…319).
  - **Y kilidi tuşu:** Basınca kilit ayağının altındaki bloğa çekilir; *Belirli Y*'de sayı da değişir. Varsayılan atanmamış.
  - **İnen köprü:** Y kilidi açıkken eğilme tuşuna her basışta kilitli Y bir blok iner; köprü bir basamak aşağıdan sürer, **Kenarda dur** basamaktan inmene izin verir. Yalnız senin bastığın eğilme tuşu sayılır. Varsayılan kapalı.
  - **Rampa:** *Kapalı* / *Yukarı* / *Aşağı*. İlerlediğin her blokta köprü bir blok yükselir ya da alçalır; yön ilk yürüdüğün yönden alınır. Y kilidi açıksa kilit de birlikte kayar. Yukarı giderken her basamakta zıpla (yürürken zıplama tuşunu basılı tutmak yeter). Her basamak iki blok yer: basamak ve altındaki. Açıkken kule yapılmaz.
  - **Kule:** Dümdüz yukarı zıplayınca bir blok yükselmişken altına blok konur, üstüne inersin (oyunun kendi zıplaması). **Sadece zıplama tuşu basılıyken** (varsayılan açık).
- **Köprünün biçimi:**
  - **Genişlik:** 1–3 (varsayılan 1). 3 iki yanına da birer blok taşar, 2 daha yakın olduğun yana.
  - **Çapraz yürüyüş:** Çapraz giderken yalnız kenarlarıyla değecek iki bloğun arasındaki köşe de doldurulur; bu olmadan köprü ilk köşede kesilir. Varsayılan açık.
- **Malzeme:**
  - **Kullanılacak bloklar:** Bu sırayla (varsayılan parke taşı, kırık derin kayrak, toprak, netherrak).
  - **Asla kullanma:** Düşen bloklar (kum, çakıl, beton tozu…), TNT, sandık/shulker gibi konteynerler. Yalnız üstüne basılabilen tam bloklar kullanılır.
  - **Tam yığını tercih et**, **Hiçbiri yoksa** (*En büyük yığın* / *En yakın* / *Hiçbir şey*).
  - **Aynı blok:** Üstünde durduğun bloktan envanterinde varsa önce o kullanılır; yoksa liste karar verir. Varsayılan kapalı.
- **Aydınlatma:**
  - **Meşale koy:** Köprünün her **Kaç blokta bir** (1–64, varsayılan 12) bloğunda envanterindeki bir meşale köprünün yan yüzüne konur — yürüme yolunun altında, önünü kapatmaz. Orada yer yoksa bir sonraki bloğun yanına konur. Meşale kalmayınca bir kez söylenir. Varsayılan kapalı.
- **Güvenlik:**
  - **Kenarda dur (SafeWalk):** Oyunun kendi eğilmesini basılı tutar. *Kenarda*: yalnız gittiğin adımın altında blok yokken (blok bitti, alanın dışı…), kenardan geri çekilene ya da kayma bitene kadar. *Her zaman*: modül açıkken hep. Varsayılan kapalı.
  - **Ekran açıkken / Düşerken / Lavda duraklat** (hepsi varsayılan açık). Düşüş bir zıplamadan uzun olanıdır.
  - Yapı Modu açık ve alan seçiliyse blok yalnız alanın içine konur.
- **Bilgi:**
  - **Kalan blok sayısı:** İskele'nin HUD kutusunda kaç blok kaldığını gösterir; **Şunun altında uyar** (varsayılan 16) altında ve bloklar bitince kırmızı uyarı.
  - **Sonraki bloğu çerçevele:** Bir sonraki bloğun konacağı yeri yeşil kutuyla gösterir. Yalnız gerçekten konabilecek bir bloğu gösterir.
  - **Eylem çubuğunda da göster:** HUD kutusundaki satır hotbar'ın üstünde de söylenir (varsayılan kapalı).
- **El ve envanter:** **Arama kapsamı** (hotbar / tüm envanter), **Eski eşyaya dön**. El kilidinde Otomatik Yeme ve Otomatik Alet'in üstünde, Totem'in altında.

### HUD (`hud`)
*Ekranda bilgi kutuları: hangi modüller açık ve ne söylüyorlar. Varsayılan açık; F1'de ve hata ayıklama ekranı (F3) açıkken gizlenir.*

- Her kutunun kendi başlığı altında: **Göster**, **Köşe** (sol üst, üst orta, sağ üst, sol alt, alt orta, sağ alt), **Yatay** / **Dikey** (köşeden içeri uzaklık), **Boyut (%)** (50–300).
- **Açık modüller:** açık modüllerin listesi (varsayılan sağ üst); **Sıralama** ada göre / en uzun önce / menüdeki sırayla.
- **İskele:** kalan blok ve kilitli Y (İskele açıkken); kilit tuşu, inen köprü, "meşale bitti" ve "dayanacak blok listede yok" satırları 3 sn en üstte.
- **Totemler:** taşınan totem sayısı (envanter + iki el); 0'da kırmızı.
- **Oto Silah:** dövüş kilidi açıksa kalan saniye, değilse "kilit yok".
- **Damar Kırıcı:** iş sürerken blok sayısı ve yüzde; işler arası kutu yok.
- **Dünya İndirici:** indiriliyor / indirilmiyor + kaydedilen chunk; sunucu izin vermiyorsa kırmızı uyarı.
- Bu modüllerin kendi ayarlarında **Eylem çubuğunda da göster**: satır hotbar'ın üstünde de söylenir.
- **HUD düzenleyici:** Menü çubuğunda **HUD'u düzenle**. Kutuyu sol tıkla tutup sürükle; bırakınca en yakın köşeden ölçülür, kenara 6 birimden yakınsa yapışır. Sağ tık kutuyu başladığı yere koyar; **Hepsini sıfırla** hepsini; **Bitti** menüye döner.

---

## 4. Profiller, Sunucuya Göre Profil ve Hazır Ayarlar

### Profiller
- Menü çubuğunun solunda **Profil: <ad> ▾**: tıklayınca profiller listelenir, birine tıklayınca ona geçilir. **Yeni** (her ayar varsayılan), **Kopyala** (kullanılanın kopyası), **Yeniden adlandır**, **Sil** (önce sorar). Ad çubuğa yazılır; Enter ya da **Tamam** alır, Escape ya da **Vazgeç** bırakır.
- Geçişte açık modüller kendi kapatmalarıyla kapanır, sonra yeni profilin ayarları okunur; orada açık olanlar açılır.
- Her profil `config/survivalkit/profiles/<ad>.json`. **default** ve **Güvenli** yerleşiktir: yeniden adlandırılamaz, silinemez.
- **Güvenli:** ona her geçişte ve oyun onunla açılınca her modül kapalıdır (HUD dahil).

### Sunucuya göre profil
- Profil listesinin son satırı **Sunucuya göre…** kurallar ekranını açar.
- **Tek oyunculu:** tek oyunculu dünyaya girince geçilecek profil. **Hiçbir kural tutmazsa:** kurallardan hiçbiri sunucuyu tutmazsa. İkisinde de **Kullanılanda kal** seçilebilir (varsayılan).
- **Adres kuralları:** adres → profil, yukarıdan aşağı; ilk tutan geçerli. **+ Kural ekle**, ✕ siler. `*` her şeyi tutar: `*.ornek.net` hem `ornek.net`'i hem altındaki her adresi tutar. Port yalnız kuralda yazılıysa sayılır. Profil düğmesi: sol tık sonraki profil, sağ tık önceki.
- Dünyaya girince seçilen profil kullanılandan başkaysa ona geçilir ve sağ üstte kısa bir bildirim çıkar: hangi profil, hangi kural (ya da tek oyunculu / kural tutmadı).
- Kendiliğinden hiçbir sunucu için kural yoktur; yazdığın kurallar `config/survivalkit/profiles.json`'da.

### Hazır ayarlar
- Her modülün ayarlarının başında **Hazır ayarlar**: yerleşikler, kendi kaydettiklerin ve **+ Kaydet**.
- Yerleşikler — İskele: **Köprü** (genişlik 1, Y kilidi başladığın yer, kenarda dur), **Rampa** (rampa yukarı, genişlik 1), **Geniş yol** (genişlik 3, Y kilidi başladığın yer, meşale açık); Köprü ve Geniş yol rampayı da kapatır. Oto Silah: **Sadece nişan**, **Çoğunluk**. Otomatik Yeme: **Tasarruflu** (eşik modu otomatik, listede yoksa önce ucuz olan), **Hep tok** (sabit eşik 19, iyileşmek için erken ye).
- Tıklamak yalnız o modülün, yalnız hazır ayarın söylediği ayarlarını değiştirir; modülü açıp kapatmaz, tuşlarına dokunmaz. Başlığın sağında kaç ayarın değiştiği yazar. Üstüne gelince ne değişeceğini satır satır gösterir.
- **+ Kaydet:** modülün o anki ayarlarını (açık/kapalı ve tuşlar hariç) bir adla saklar; kendi hazır ayarının ✕'i siler. Kendi hazır ayarların `config/survivalkit/presets.json`'da ve her profilde aynıdır.
- Değişen her ayarın ↺'i onu tek başına hazır ayardan önceki değerine döndürür.
