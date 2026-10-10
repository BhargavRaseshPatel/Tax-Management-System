package com.propertytax.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.propertytax.property.Property;
import com.propertytax.property.PropertyClass;
import com.propertytax.property.PropertyOwnerRepository;
import com.propertytax.taxaccount.TaxAccount;
import com.propertytax.taxbill.TaxBill;
import com.propertytax.taxbill.TaxBillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Generate-once and self-heal logic with mocked storage: no Spring, no S3, no database. */
@ExtendWith(MockitoExtension.class)
class TaxBillDocumentServiceTest {

	static final String KEY = "tax-bills/2026/B-1.pdf";
	static final byte[] RENDERED = "rendered".getBytes();

	@Mock TaxBillRepository taxBills;
	@Mock PropertyOwnerRepository propertyOwners;
	@Mock TaxBillPdfRenderer renderer;
	@Mock DocumentStore store;
	@InjectMocks TaxBillDocumentService service;

	TaxBill bill;

	@BeforeEach
	void setUp() {
		var property = new Property("RN-1", "1 Demo St", "Demo City", "L5B 1A1", PropertyClass.RESIDENTIAL);
		ReflectionTestUtils.setField(property, "id", 7L);
		bill = new TaxBill("B-1", new TaxAccount("TA-1", property), null, null, 2026, TaxBill.Type.FINAL,
				new BigDecimal("100.00"), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
		when(taxBills.require(1L)).thenReturn(bill);
	}

	void rendererProducesPdf() {
		when(propertyOwners.findByPropertyIdOrderById(7L)).thenReturn(List.of());
		when(renderer.render(bill, List.of())).thenReturn(RENDERED);
	}

	@Test
	void firstDownloadRendersUploadsAndRecordsTheKey() {
		rendererProducesPdf();

		var document = service.download(1L);

		assertThat(document.fileName()).isEqualTo("B-1.pdf");
		assertThat(document.content()).isEqualTo(RENDERED);
		verify(store).put(KEY, RENDERED, "application/pdf");
		assertThat(bill.getDocumentKey()).isEqualTo(KEY);
		assertThat(bill.getDocumentSizeBytes()).isEqualTo(RENDERED.length);
	}

	@Test
	void storedDocumentIsReturnedWithoutRenderingAgain() {
		bill.attachDocument(KEY, 6, Instant.parse("2026-06-01T00:00:00Z"));
		when(store.get(KEY)).thenReturn(Optional.of("stored".getBytes()));

		assertThat(service.download(1L).content()).isEqualTo("stored".getBytes());
		verify(renderer, never()).render(any(), any());
		verify(store, never()).put(any(), any(), any());
	}

	@Test
	void missingObjectIsRebuiltFromTheDatabase() {
		var originallyGenerated = Instant.parse("2026-06-01T00:00:00Z");
		bill.attachDocument(KEY, 6, originallyGenerated);
		when(store.get(KEY)).thenReturn(Optional.empty()); // e.g. emulator reset, or the object was deleted
		rendererProducesPdf();

		assertThat(service.download(1L).content()).isEqualTo(RENDERED);
		verify(store).put(KEY, RENDERED, "application/pdf");
		assertThat(bill.getDocumentGeneratedAt()).isAfter(originallyGenerated);
	}

}
