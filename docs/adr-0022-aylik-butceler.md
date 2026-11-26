# ADR-0022 · Aylık bütçeler

- **Durum** Uygulandı (2026-11-26)
- **Karar** Her gider kategorisi için tek, pozitif kuruş limiti saklanır. Genel limit, sabit kimlikli ayrı bir Room satırıdır. Bütçe kaldırılınca satır silinir. Limitler her ay geçerlidir ve geçmiş aylar da mevcut limitlerle karşılaştırılır.
- **Veri** Room v3 → v4 iki boş tablo ekler. Kategori silinince dış anahtar bütçeyi siler. İşlemler ve tekrarlayan kurallar “Diğer”e taşınırken “Diğer”in limiti korunur. JSON v4 iki limit türünü aynı Room işlemiyle yedekler ve geri yükler. V1–v3 dosyaları bütçesiz açılır.
- **Özet** Genel bütçe tüm giderleri kapsar. Yüzde sıralamasında kesin oran kullanılır ve en yüksek üç kategori gösterilir. %80–100 arası uyarı, %100 üzeri aşım sayılır. Satırlar ortak seçili ayda İşlemler’in gider ve kategori filtrelerini açar.
