# ADR-0023 · Aylık gelir ve gider eğilimi

- **Durum** Uygulandı (2026-11-26)
- **Bağlam** Ayın gelir, gider ve neti son aylarla, kategori tutarları bir önceki takvim ayıyla karşılaştırılabilmeli.
- **Karar** Özet, ortak seçili ayla biten altı takvim ayını gösterir. Compose Canvas gelirleri dolu, giderleri çerçeveli çizer. Boş aylar grafikte yerini korur. Bir çubuk çiftine dokunmak ortak ay seçimini değiştirir.
- **Karşılaştırma** Özet iki ayın netini ve giderdeki yüzde değişimini, Dağılım listelenen kategorilerin yüzde değişimini gösterir. Önceki takvim ayındaki tutar sıfırsa yüzde yerine “—” görünür.
- **Veri** Tarih indeksini kullanan tek sorgu, aralıktaki işlemlerin gerekli alanlarını okur. Kotlin takvim aylarını ve kuruş toplamlarını hesaplar. Transferler ayrı tablodadır. Room şeması ve JSON yedek biçimi aynı sürümde kalır.
