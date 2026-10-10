package com.propertytax.document;

import java.time.Instant;

import com.propertytax.property.PropertyOwnerRepository;
import com.propertytax.taxbill.TaxBill;
import com.propertytax.taxbill.TaxBillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaxBillDocumentService {

	public record BillDocument(String fileName, byte[] content) {}

	private final TaxBillRepository taxBills;
	private final PropertyOwnerRepository propertyOwners;
	private final TaxBillPdfRenderer renderer;
	private final DocumentStore store;

	TaxBillDocumentService(TaxBillRepository taxBills, PropertyOwnerRepository propertyOwners,
			TaxBillPdfRenderer renderer, DocumentStore store) {
		this.taxBills = taxBills;
		this.propertyOwners = propertyOwners;
		this.renderer = renderer;
		this.store = store;
	}

	/**
	 * Returns the bill's PDF, generating and storing it on first request. If the stored object has gone missing it
	 * is rebuilt: the PDF is derived entirely from database rows, so storage is never the only copy of anything.
	 */
	// ponytail: renders and uploads inside the request and its transaction; move to the async worker (Stage 13)
	// if bills become numerous or large. Two simultaneous first requests both upload the same bytes to the same key.
	@Transactional
	public BillDocument download(Long billId) {
		var bill = taxBills.require(billId);
		var fileName = bill.getBillNumber() + ".pdf";
		if (bill.getDocumentKey() != null) {
			var stored = store.get(bill.getDocumentKey());
			if (stored.isPresent()) {
				return new BillDocument(fileName, stored.get());
			}
		}
		var pdf = renderer.render(bill, propertyOwners.findByPropertyIdOrderById(bill.getTaxAccount().getProperty().getId()));
		var key = keyFor(bill);
		store.put(key, pdf, "application/pdf");
		bill.attachDocument(key, pdf.length, Instant.now());
		return new BillDocument(fileName, pdf);
	}

	/** No personal data in keys: year and bill number only. */
	static String keyFor(TaxBill bill) {
		return "tax-bills/" + bill.getTaxYear() + "/" + bill.getBillNumber() + ".pdf";
	}

}
