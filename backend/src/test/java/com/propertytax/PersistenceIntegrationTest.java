package com.propertytax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.propertytax.assessment.Assessment;
import com.propertytax.assessment.AssessmentRepository;
import com.propertytax.owner.Owner;
import com.propertytax.owner.OwnerRepository;
import com.propertytax.payment.Payment;
import com.propertytax.payment.PaymentRepository;
import com.propertytax.property.Property;
import com.propertytax.property.PropertyClass;
import com.propertytax.property.PropertyOwner;
import com.propertytax.property.PropertyOwnerRepository;
import com.propertytax.property.PropertyRepository;
import com.propertytax.taxaccount.TaxAccount;
import com.propertytax.taxaccount.TaxAccountRepository;
import com.propertytax.taxbill.TaxBill;
import com.propertytax.taxbill.TaxBillRepository;
import com.propertytax.taxrate.TaxRate;
import com.propertytax.taxrate.TaxRateRepository;
import com.propertytax.user.User;
import com.propertytax.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/** Persists the full User → … → Payment chain against real PostgreSQL. Each test rolls back. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PersistenceIntegrationTest {

	@Autowired EntityManager em;
	@Autowired UserRepository users;
	@Autowired OwnerRepository owners;
	@Autowired PropertyRepository properties;
	@Autowired PropertyOwnerRepository propertyOwners;
	@Autowired AssessmentRepository assessments;
	@Autowired TaxRateRepository taxRates;
	@Autowired TaxAccountRepository taxAccounts;
	@Autowired TaxBillRepository taxBills;
	@Autowired PaymentRepository payments;

	User officer;
	Owner owner;
	Property property;
	Payment payment;

	/** Fictional demo data only. */
	void persistChain() {
		officer = users.save(new User("Officer@Demo.test", "Olivia Officer", User.Role.TAX_OFFICER));
		User resident = users.save(new User("resident@demo.test", "Riley Resident", User.Role.RESIDENT));
		owner = owners.save(new Owner(resident, "Riley Resident", "resident@demo.test", null, "1 Demo St"));
		property = properties.save(new Property("RN-0001", "1 Demo St", "Demo City", "L5B 1A1", PropertyClass.RESIDENTIAL));
		propertyOwners.save(new PropertyOwner(property, owner, new BigDecimal("100.00")));
		var assessment = assessments.save(new Assessment(property, 2026, new BigDecimal("650000.00"), LocalDate.of(2026, 1, 1)));
		var rate = taxRates.save(new TaxRate(2026, PropertyClass.RESIDENTIAL, new BigDecimal("0.00750000"), "Demo rate"));
		var account = taxAccounts.save(new TaxAccount("TA-0001", property));
		var bill = taxBills.save(new TaxBill("BILL-2026-F-0001", account, assessment, rate, 2026, TaxBill.Type.FINAL,
				new BigDecimal("4875.00"), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1)));
		payment = payments.save(new Payment(bill, new BigDecimal("1000.00"), LocalDate.of(2026, 6, 15),
				Payment.Method.CHEQUE, "REF-0001", officer));
		em.flush();
		em.clear(); // force every read below to come from the database
	}

	@Test
	void fullChainRoundTripsThroughPostgres() {
		persistChain();

		var saved = payments.findById(payment.getId()).orElseThrow();
		assertThat(saved.getStatus()).isEqualTo(Payment.Status.RECORDED);
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getRecordedBy().getEmail()).isEqualTo("officer@demo.test");

		var bill = saved.getTaxBill();
		assertThat(bill.getStatus()).isEqualTo(TaxBill.Status.ISSUED);
		assertThat(bill.getAmount()).isEqualByComparingTo("4875.00");
		assertThat(bill.getTaxRate().getRate()).isEqualByComparingTo("0.0075");
		assertThat(bill.getAssessment().getTaxYear()).isEqualTo(2026);
		assertThat(bill.getTaxAccount().getProperty().getRollNumber()).isEqualTo("RN-0001");

		assertThat(owners.findById(owner.getId()).orElseThrow().getUser().getRole()).isEqualTo(User.Role.RESIDENT);
	}

	@Test
	void relationshipsAreLazyUnlessExplicitlyFetched() {
		persistChain();

		var plain = propertyOwners.findAll().getFirst();
		assertThat(Hibernate.isInitialized(plain.getProperty())).isFalse();
		em.clear();

		var fetched = propertyOwners.findByOwnerId(owner.getId());
		assertThat(fetched).hasSize(1);
		assertThat(Hibernate.isInitialized(fetched.getFirst().getProperty())).isTrue();
		assertThat(fetched.getFirst().getProperty().getPostalCode()).isEqualTo("L5B 1A1");
	}

	@Test
	void duplicatePaymentReferenceIsRejected() {
		persistChain();
		var bill = taxBills.findAll().getFirst();
		var duplicate = new Payment(bill, new BigDecimal("1000.00"), LocalDate.of(2026, 6, 15),
				Payment.Method.CHEQUE, "REF-0001", users.getReferenceById(officer.getId()));

		assertThatThrownBy(() -> payments.saveAndFlush(duplicate))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

}
