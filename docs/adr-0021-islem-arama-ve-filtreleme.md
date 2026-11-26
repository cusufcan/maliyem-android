# ADR-0021 · İşlemlerde arama ve filtreleme

- **Durum** Uygulandı (2026-11-26)
- **Bağlam** İşlemler, ortak ay görünümünde ve daha geniş tarih aralıklarında bulunabilmeli.
- **Karar** Room önce mevcut tarih indeksleri üzerinden tarih, tür, kategori ve hesap koşullarını uygular. Ardından Kotlin not, ekranda görünen adlar ve lira önekiyle arar. Arama Türkçe I biçimlerini ve aksanları eşitler, tutarda virgül ve noktayı kabul eder. Tutar sınırları dahil ve kuruş cinsindendir.
- **Kapsam** Varsayılan aralık Özet ile ortak seçili aydır. Arama seçili aralığı kullanır. Son üç ay bugünden üç ay öncesine uzanır. Kategori seçilince kategorisiz transferler elenir. Hesap seçimi transferin iki ucunu da kapsar. Transferler sonuç sayısına girer ve gelir-gider toplamlarını etkilemez.
- **Durum saklama** Arama ve uygulanan filtreler ekranın `SavedStateHandle` alanındadır. Döndürmede aynı süreçte korunur. Yeni süreç, Android eski ekranı geri yüklese de varsayılan seçime döner. Room şeması ve JSON yedek biçimi aynı kalır.
