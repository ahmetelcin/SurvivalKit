# SurvivalKit — Değişiklik Günlüğü (Changelog)

SurvivalKit modunun sürümler arası değişiklikleri.

---

## [0.13.1] - 2026-10-29

### Düzeltildi
- **Damar Kırıcı, alet aşınması:** 26.3'te her kırımda sohbete "Failed to parse structure" hatası düşüyor, alet aşınmıyordu. Kurulan 0.13.0 jar'ı, `/item modify` sözdizimi düzeltmesinden (`function` → `type`) önce derlenmişti; bu sürüm düzeltmeyi içerir.


## [0.13.0] - 2026-10-27

### Değişti
- **Minecraft 26.3'e geçildi** (Fabric Loader 0.19.5, Fabric API 0.162.0+26.3). 26.2 sürümünde kalan 0.12.1 bu oyunda açılmıyordu.
- Tuşlar 26.3'ün tuş tablosuyla adlandırılır (`key.keyboard.*` adları aynı kalır; kayıtlı ayarlar olduğu gibi okunur). Menü tuşu varsayılanı Sağ Shift olarak kalır.
- Basılı fare düğmesi SDL'den okunur; fare düğmeleri 1'den başlar (sol = 1, sağ = 3, orta = 2).
- Eski sürümlerin sayısal GLFW tuş kodları `LegacyGlfwKeys` ile adlandırılır: harfler, rakamlar, F1–F12 ve yaygın değiştiriciler. Bunların dışındaki eski kodlar atanmamış sayılır.
- İskele ve dünya indirici 26.3 API'sine uyarlandı (kol sallama `SwingSource.PREDICTED`, saat `clockManager().getInstance()`); davranış aynı.


## [0.12.1] - 2026-10-11

### Değişti
- **İskele, Neyin yanına → Sadece listedekiler:** Liste artık köprünün nereden başlayacağını belirler. İskele'nin bu köprüde kendi koyduğu bloklar listede olmasa da dayanak sayılır, köprü kendi üstünde sürer (kırılıp yerine elle başka bir şey konan ya da 16 bloktan uzakta kalan blok sayılmaz).
- **Boş liste kısıtlama değil:** *Sadece listedekiler* seçili ve liste boşsa her bloğun yanına konur. Listenin altında "Liste boş: her bloğun yanına konur" yazar.

### Düzeltildi
- İskele'nin yeşil çerçevesi konamayacak bir bloğu da gösteriyordu (ör. *Sadece listedekiler* + boş liste): çerçeve çıkıyor, blok konmuyordu. Artık yalnız gerçekten konabilecek bloğu gösterir.
- Yanında dayanacak blok varken liste hepsini reddederse HUD'da kırmızı "İskele: dayanacak blok listede yok" (bir kez; bir blok konunca yeniden söylenebilir).
- Menüde bir listenin "+ Ekle" satırının altına tıklamak da öğe seçiciyi açıyordu; artık yalnız "+ Ekle" açar.

---

## [0.12.0] - 2026-10-08

### Eklendi
- **HUD (`hud`, Görsel, varsayılan açık):** Ekranda bilgi kutuları; F1'de ve F3 açıkken gizli. Her kutunun kendi başlığı altında *Göster*, *Köşe* (6 köşe/kenar), *Yatay* / *Dikey* kayma, *Boyut* (%50–300).
  - *Açık modüller:* açık modüllerin listesi (sağ üst), sıralama ada göre / en uzun önce / menüdeki sırayla.
  - *İskele:* kalan blok ve kilitli Y İskele açıkken hep; kilit tuşu, inen köprü, meşale bitti satırları 3 sn en üstte.
  - *Totemler:* taşınan totem sayısı (0'da kırmızı). *Oto Silah:* dövüş kilidinin kalan saniyesi ya da "kilit yok". *Damar Kırıcı:* iş sürerken blok sayısı ve yüzde. *Dünya İndirici:* indiriliyor / indirilmiyor + chunk sayısı, sunucu reddederse kırmızı uyarı.
  - Bu dört modülde ve İskele'de **Eylem çubuğunda da göster** (varsayılan kapalı): HUD'daki satır hotbar'ın üstünde de söylenir.
- **HUD düzenleyici:** Menünün üstündeki çubukta **HUD'u düzenle**. Kutuları sürükleyerek taşı; bırakınca en yakın köşeden tutulur, kenara 6 birimden yakınsa yapışır. Sağ tık kutuyu başladığı yere, **Hepsini sıfırla** hepsini koyar.
- **Profiller:** Menü çubuğunun solunda profil seçici (liste), **Yeni**, **Kopyala**, **Yeniden adlandır**, **Sil** (önce sorar). Her profil `config/survivalkit/profiles/<ad>.json`; geçişte açık modüller kendi kapatmalarıyla kapanır (tutulan tuş bırakılır, ödünç el geri verilir), yeni profil temiz bir zeminden okunur. Eski `survivalkit.json` bir kez "default"a alınır.
- **Güvenli profil (yerleşik):** Ona her geçişte ve oyun onunla açılınca her modül kapalı (HUD dahil). Yeniden adlandırılamaz, silinemez.
- **Sunucuya göre profil:** Profil listesinin son satırı **Sunucuya göre…**: tek oyunculu için bir profil, hiçbir kural tutmazsa bir profil (ikisi de varsayılan olarak "kullanılanda kal") ve yukarıdan aşağı adres kuralları (`*` joker: `*.ornek.net` hem alt adresleri hem `ornek.net`'i tutar; port yalnız yazılıysa sayılır). Dünyaya girince seçilen profile geçilir, sağ üstte kısa bir bildirim: hangi profil ve neden. Kendiliğinden hiçbir sunucu için kural yoktur.
- **Hazır ayarlar:** Her modülün ayarlarının başında. Yerleşikler — İskele: **Köprü** (genişlik 1, Y kilidi başladığım yer, kenarda dur), **Rampa** (yukarı, genişlik 1), **Geniş yol** (genişlik 3, Y kilidi, meşale); Oto Silah: **Sadece nişan**, **Çoğunluk**; Otomatik Yeme: **Tasarruflu** (otomatik eşik, önce ucuz olan), **Hep tok** (sabit eşik 19, iyileşmek için erken ye). **+ Kaydet** modülün o anki ayarlarını kendi hazır ayarın olarak saklar (`config/survivalkit/presets.json`, her profilde aynı), ✕ siler. Uygulamak yalnız o modülü değiştirir, açık/kapalısına ve tuşlarına dokunmaz; üstüne gelince ne değişeceğini söyler.
- **↺ hazır ayardan sonra:** Hazır ayarın değiştirdiği her satırın ↺'i o ayarı tek başına hazır ayardan önceki değerine döndürür (sonra yine varsayılana).

### Değişti
- İskele'nin kalan blok sayısı artık İskele'nin HUD kutusunda; eylem çubuğu yalnız *Eylem çubuğunda da göster* açıkken. Ayarın adı **Kalan blok sayısı**.
- Menünün panelleri üstteki çubuk için 20 birim aşağıda.

### Düzeltildi
- Dünya İndirici'nin chunk sayısı kaydetme çağrılarını sayıyordu; durunca ikiye katlanıyordu. Artık ayrı chunk sayılıyor (`/wdl status` da).

---

## [0.11.0] - 2026-10-07

### Eklendi
- **İskele — Köprünün biçimi:** **Genişlik** 1 / 2 / 3 (3 iki yana, 2 daha yakın olunan yana); **Çapraz yürüyüş** (varsayılan açık): çapraz giderken yalnız kenarıyla değecek iki bloğun arasına köşe bloğu — bu olmadan köprü ilk köşede kesiliyordu.
- **İskele — Rampa** (*İleri ve yukarı*): *Kapalı* / *Yukarı* / *Aşağı*. İlerlenen her blokta köprü bir yukarı ya da aşağı; Y kilidi açıksa kilit de kayar. Her basamak iki blok (basamak + altındaki destek). Yukarı rampada her basamakta zıplanır. Açıkken kule yok.
- **İskele — İnen köprü** (varsayılan kapalı): Y kilidi açıkken eğilme tuşuna basınca kilitli Y bir iner; Kenarda dur bir alt basamağı kenar saymaz.
- **İskele — Aynı blok** (*Malzeme*, varsayılan kapalı): üstünde durulan blok envanterde varsa önce o.
- **İskele — Meşale** (*Aydınlatma*, varsayılan kapalı): her N blokta (varsayılan 12) köprünün yan yüzüne duvar meşalesi, yürüme yolunun altında. Yer yoksa sayaç durur, bir sonraki blokta yeniden denenir. Meşale bitince bir kez uyarır.

---

## [0.10.0] - 2026-10-06

### Eklendi
- **İskele — Y kilidi** (*İleri ve yukarı*): *Kapalı* / *Başladığım yerin Y'si* (İskele açıldığında ayağın altındaki blok) / *Belirli Y* (−64…319). Açıkken her blok o Y'ye konur; zıplarken ve koşarak zıplarken de köprü düz kalır (erişim yettiği sürece). Kilit açıkken kule yapılmaz.
- **İskele — Y kilidi tuşu** (varsayılan atanmamış): kilidi ayağın altındaki bloğa çeker; *Belirli Y*'de sayı ayarı da değişir ve kaydedilir.
- Kilit açıkken eylem çubuğunda kalan blok sayısının yanında kilitli Y; tuşa basınca "Y kilidi <n>".

---

## [0.9.0] - 2026-10-06

### Eklendi
- **İskele (`scaffold`, Hareket):** Yürürken bir sonraki adımın altı boşsa oraya blok koyar. Bakış hiç döndürülmez: blok, yandaki bloğun yüzüne oyunun kendi yerleştirmesiyle (`useItemOn`) konur; hız, zıplama ve sunucuya giden hareket bilgisi değiştirilmez.
  - *Yerleştirme:* gecikme, tick başına blok, hava / su / lav ayrı tikler, neyin yanına (her blok / sadece listedekiler). Kullanılınca kendi işini yapan bloklar (sandık, kapı, şalter…) destek sayılmaz.
  - *İleri ve yukarı:* ileri uzat (0–5), Y seviyesini koru, kule (yalnız oyunun zıplamasıyla; "sadece zıplama tuşu basılıyken").
  - *Malzeme:* sıralı tercih listesi, kara liste (düşen bloklar, TNT, sandık/shulker gibi konteynerler), tam yığını tercih et, yedek seçim.
  - *Güvenlik:* kenarda dur (SafeWalk: kapalı / kenarda / her zaman — oyunun kendi eğilmesi), ekran açıkken / düşerken / lavda duraklat; Yapı Modu alanı seçiliyse yalnız alanın içine.
  - *Bilgi:* eylem çubuğunda kalan blok, eşiğin altında ve bitince kırmızı uyarı; sonraki blok yeşil kutuda.
  - El kilidinde Otomatik Yeme ve Otomatik Alet'in üstünde, Totem'in altında.

---

## [0.8.1] - 2026-10-03

### Eklendi
- **Damar Kırıcı — Alet aşınsın** (Sınırlar, varsayılan açık): ana eldeki alet her bloğu elle kırmışsın gibi aşınır (blok başına aletin hasarı, Kırılmazlık oyundaki oranla). Sunucunun `/item` komutuyla eklenir, `/item` yetkisi aranır. Alet asla kırılmaz: aleti tüketecek iş başlamaz, sohbete yazar.

---

## [0.8.0] - 2026-10-03

### Eklendi
- **Yapı Modu (`buildmode`):** Değnekle (varsayılan tahta kılıç, eşya seçiciyle değişir) alan seçimi: açıkken sol tık 1. köşe, sağ tık 2. köşe; tıklar blok kırmaz, koymaz. Sohbete köşelerin koordinatı, alanın boyutu ve hacmi yazılır. Alan dünyada çerçeveyle görünür, her sunucu ve boyut için ayrı saklanır. Kapalıyken değnek sıradan eşya, çerçeve gizli, alan hiçbir şeyi kısıtlamaz.
  - `/bolge` (`goster`), `/bolge temizle`, `/bolge kose1`, `/bolge kose2`.
  - Açıkken Oto Silah ve Otomatik Alet değneğe dokunmaz.
- **Damar Kırıcı — Sınırlar:**
  - *Alan yoksa* (*Her yerde çalış* / *Hiç çalışma*): Yapı Modu alanı geçerliyken yalnız içindeki bloklar kırılır, dışarı taşan kısım kalır.
  - *Kaç blokta bir açlık puanı* (varsayılan 10): bedel sunucunun `/effect` komutuyla kısa bir Açlık etkisi olarak ödenir; `/effect` yetkisi de aranır; açlık çubuğu 7'nin altına inecekse iş başlamaz ve sohbete yazar.

---

## [0.7.0] - 2026-10-02

### Değişti
- **Oto Silah:**
  - Dövüş kilidi yalnız saldırı bekleme süresini sıfırlayacak geçişi tutar; aynı eşyanın iki kopyası arasında geçiş ve bekleme süresi dolduktan sonraki geçiş serbest (phantom'a dönünce silahın takılı kalması düzeldi).
  - Yeni *Seçim kaynağı* (varsayılan *Nişan, yoksa çevredeki çoğunluk*): nişan yoksa menzildeki düşman moblar sayılır, çoğunluğun silahı ele alınır; yeni çoğunluk en az 1 saniye sürmeden silah değişmez. *Menzil* ayarı hazırlık slotunun eski *Mesafe*'sinin yerini aldı.
- **Otomatik Yeme:** *Yedek mod* ayarının adı *Listede hiçbiri yoksa*, *Hiçbiri* seçeneği *Hiçbir şey yeme*.
- Tooltip metinlerinde noktalama ve hitap birliği.

### Eklendi
- `docs/KULLANIM.md`, `docs/USAGE.md` kullanım kılavuzları ve bu değişiklik günlüğü.
- **Test altyapısı:** `./gradlew runClientGameTestParallel` gametest'leri 4 istemcide paralel koşturur (tam tur ~8,5 dk → ~3 dk 10 sn); parçalar ölçülen sürelerle dengelenir, düşen istemcinin log'u ve ekran görüntüleri saklanır. Her testten önce modüller varsayılana döner (`GameTestBoundary`).

---

## [0.6.0] - 2026-10-02

### Eklendi
- **Damar Kırıcı (`veinminer`):** Bütün bir ağacı ya da cevher damarını sunucunun kendi komutlarıyla (`/loot`, `/give`, `/fill`, `/setblock`) bir seferde kırar; `/setblock` yetkisi yoksa hiçbir şey yapmaz.
  - İki tetik: *Basılı tutup kır* (tuş basılıyken normal kırılan kütüğün/cevherin gerisi gider) ve *Bakıp bas*.
  - Gidecek blokların çevresine çerçeve.
  - Yalnız doğal ağaçlar (dünyanın büyüttüğü yaprağa değen kütükler); bir seferde en fazla blok sınırı; saniyede en fazla komut sınırı; sunucu yanıtlarını gizleme.
  - `/loot`'suz sunucuda: İpeksi Dokunuşla bloklar `/give` ile; Servetli kazmayla cevher eşyaları vanilla kurallarıyla hesaplanıp `/give`, XP'si `/xp` ile; ikisi de yoksa eşyalar bloğun yerine düşer.

---

## [0.5.0] - 2026-10-01

### Eklendi
- **Dünya İndirici (WDL) İkinci Tur:**
  - **Aynı dünyaya devam et (`continue_world`):** Aynı sunucudan sonraki indirmeler yeni klasör açmak yerine mevcut dünya kaydına eklenir. Yeniden görülen chunk'lar güncellenir, görülmeyenler ve önceden açılan sandıkların içi korunur.
  - **Harita kaydı:** Oyuncunun elinde tuttuğu veya eşya çerçevesinde görülen doldurulmuş haritaların verileri `data/map_<id>.dat` dosyaları olarak kaydedilir.
  - **Binek ve vagon envanterleri:** At, eşek, katır, lama, sandıklı vagon ve hunili vagon envanterleri oyuncu tarafından açıldığında yakalanır ve kaydedilir.
  - **Dünya bilgileri:** Gündüz/gece saat bilgisi, hava durumu (yağmur/fırtına), spawn noktası koordinatları ve dünya zorluğu yakalanıp `level.dat` içine yazılır.
  - **Sunucu reddi kontrolü:** Eski World Downloader modunun izin kanalı (`wdl:init` / `WDL|INIT`) dinlenir; sunucu indirmeyi yasaklarsa indirme başlatılmaz ve sohbet ekranında uyarı verilir (`survivalkit.wdl.forbidden`).
- **Altyapı ve Test:**
  - Gradle üzerinden sınıf adı süzgeciyle gametest koşturma desteği: `-Pgametests=...`.
  - Gametest zaman aşımı 15 dakikaya çıkarıldı.

---

## [0.4.0] - 2026-09-30

### Eklendi
- **Oto Silah (`autoweapon` — Faz 5):**
  - Hedef türüne ve zırh durumuna göre en iyi silah seçimi (Smite, Bane of Arthropods, Keskinlik büyüleri ve hasar hesaplaması).
  - Nişangâhta silah seçimi ve otomatik vurma (`auto_attack`, vuruş bekleme süresi dolduğunda).
  - Hazırlık slotu (`prep_slot`, `select_prep_slot`, `prep_range`): Belirlenen menzile giren mob için silahı önceden hotbar slotuna hazırlama.
  - Dövüş kilidi (`lock_seconds`, `lock_aim`): Hasar alındığında veya vurulduğunda silah ve slot geçişini geçici olarak dondurma.
  - Otomatik vur bekçisi (watchdog): 2 saniye aynı durumda takılı kalırsa tespit edip loglama.
- **Dünya İndirici (WDL) Birinci Tur:**
  - Konteyner yakalama: Sandık, çift sandık (`DoubleChestSplitter`), varil, shulker, fırın ve huni envanterlerinin yakalanması (`BlockEntityOverlay`).
  - Varlık kaydı: `entities/*.mca` dosyalarına eşya çerçeveleri, zırh standları ve hayvanların kaydedilmesi (oyuncu varlıkları filtrelenir).
  - Periyodik kayıt (flush ticker) ve boyut değişiminde (Nether/End) güvenli geçiş ve dosya yazımı.
  - İstemci komutları: `/wdl start`, `/wdl stop`, `/wdl status`, `/wdl save`, `/wdl help`.
  - Sunucu komut ağacında izin varsa otomatik `/seed` sorgulama (`auto_query_seed`).
  - Sahiplik ayarı: Evcil varlıkların sahipliğini olduğu gibi bırakma (`as_is`) veya yerel oyuncuya devretme (`transfer_to_local`).
  - Köylü ticaretlerini (`Offers`) yakalama ve kaydetme.
  - Oyuncu durumu kaydı: Envanter, zırh, diğer el, konum, bakış yönü, XP, can, açlık ve ender sandığının `players/data/<UUID>.dat` ve `level.dat` içine yazılması (`save_player_state`).
- **Otomatik Yeme:**
  - Ana elde ısırığı doğrudan başlatma; nişangâhtaki hedefe zarar vermeden yemeğe öncelik verme.

---

## [0.3.4] - 2026-09-30

### Eklendi
- **Otomatik Yeme İyileştirmeleri:**
  - Eşik modu ayarı (`threshold_mode`): Sabit (`fixed`) veya Otomatik (`auto`, yiyeceğin tam değerine göre doyurma).
  - İyileşmek için erken ye ayarı (`eat_early_when_hurt`, `early_eating_health`): Can azaldığında doğal yenilenmeyi sürdürmek için açlığı beklemeden yeme.
  - Açık menüde (sandık vb.) sırt çantasından hotbar'a geri takas yapabilme desteği.
- Menü "+ Ekle" satırına tıklamadan önce otomatik kaydırma düzeltmesi.
- Gametest zaman aşımı 8 dakikaya çıkarıldı.

---

## [0.3.3] - 2026-09-30

### Eklendi
- **Otomatik Alet:**
  - "Sadece elde alet varken" (`only_with_tool`) ayarı (varsayılan açık): El boşken veya elde blok/yiyecek varken gereksiz alet değişimini engelleme.
- Tooltip taraması tüm ayar ağacını kapsayacak şekilde genişletildi.

---

## [0.3.2] - 2026-09-30

### Düzeltildi
- Menü metin kutularında metnin dikeyde ortalanması ve yer tutucu metinle aynı hizada çizilmesi.
- **Otomatik Alet:** Eldeki İpeksi Dokunuşlu aletin yalnız geçerli olduğu bloklarda (ör. taşta kazma, çimende kürek) elde kalması sağlandı.

---

## [0.3.1] - 2026-09-29

### Düzeltildi
- Menü metin kutularına yazılan yazıların çerçeve altında kalması sorunu giderildi; kutular çerçeveden sonra doğru katmanda çizilmeye başlandı.

---

## [0.3.0] - 2026-09-29

### Eklendi
- **Faz 4 — Alet Altyapısı:**
  - Ortak "en iyi alet" kuralı (`BestTool`): Blok düşürme gereksinimi, tercih edilen büyü (İpeksi Dokunuş / Servet), madencilik hızı ve dayanıklılık eşiği.
  - **Otomatik Alet (`autotool`):** Blok kırmaya başlayınca en iyi aleti ele alma, kırma bitince eski eşyaya dönme, kılıcı koruma (`keep_sword`), İpeksi Dokunuş ve Servet blok listeleri.
  - **Alet Seçici (`toolpicker`):** Bloğa orta tıklayınca en uygun aleti seçme (mixin'siz temiz istemci entegrasyonu).

---

## [0.2.2] - 2026-09-29

### Eklendi
- **Ayar Gruplama Altyapısı (`SettingGroup`):**
  - Menüde ayarlar katlanabilir başlıklar altında gruplandırıldı.
  - Profil dosyalarındaki JSON anahtarları geriye dönük uyumluluk için düz yapıda korundu.

---

## [0.2.1] - 2026-09-29

### Düzeltildi
- Açılır liste (dropdown) menüsünün tek kural mantığıyla güvenilir açılması ve kapatılması.
- Gerçek fare tıklama olaylarıyla çalışan widget testleri eklendi.

---

## [0.2.0] - 2026-09-29

### Eklendi
- **Faz 2 — Menü ve Arayüz:**
  - Right Shift ile açılan iki panelli SurvivalKit menüsü.
  - Türkçe karakter yumuşatma ve arama desteği (`SearchText`).
  - Tuş atama widget'ı ve çakışma tespit sistemi.
  - Eşya seçici ekranı (`ItemPickerScreen`).
- **Faz 3 — Dil ve El Koordinasyonu:**
  - Çoklu dil sistemi: `en_us.json` ve `tr_tr.json` ile tüm arayüz ve tooltip'lerin eksiksiz çevirisi.
  - El koordinasyon sistemi (`Hands`): Birden fazla modülün aynı ele müdahale etmesini engelleyen el kilidi.
  - **Otomatik Totem (`autototem`):** Diğer elde ölümsüzlük totemi tutma, patlayınca yenileme, totem bitince yedek eşya tutma, can düşünce devreye girme ve totem uyarı sistemi.
  - Can düştüğünde totemin yemek yemeyi kesme önceliği.

---

## [0.1.0] - 2026-09-29

### Eklendi
- **Faz 1 — Temel Mod Altyapısı:**
  - Modül ve ayar mimarisi (`Module`, `Setting`, `BoolSetting`, `IntSetting`, `EnumSetting`, `RegistryListSetting`, `TextSetting`, `KeybindSetting`).
  - Profil bazlı kayıt sistemi (`SettingsStorage`).
  - Envanter analiz ve öncelik sırası mantığı (`InventoryUtil`, `ItemPriorityResolver`).
  - **Otomatik Yeme (`autoeat`) Temeli:** Açlık eşiği, tercih sırası, kara liste, yedek mod, altın elma ve büyülü altın elma kullanımı.
  - **Dünya İndirici (`wdl`) Temeli:** Sunucuda gezilen chunk'ların Anvil formatında diske yazılması, `level.dat` üretimi, tek oyunculuda doğrudan açılabilen dünya kayıtları.
