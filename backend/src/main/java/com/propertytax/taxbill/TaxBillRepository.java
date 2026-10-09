package com.propertytax.taxbill;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.propertytax.common.ApiErrors;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface TaxBillRepository extends JpaRepository<TaxBill, Long> {

	/** Sum of RECORDED payments for one bill. */
	record PaidTotal(Long billId, BigDecimal paid) {}

	List<TaxBill> findByTaxAccountIdOrderByTaxYearDescBillTypeAsc(Long taxAccountId);

	List<TaxBill> findByTaxAccountIdAndTaxYearAndStatusNot(Long taxAccountId, Integer taxYear, TaxBill.Status status);

	long countByTaxAccountIdAndTaxYearAndBillType(Long taxAccountId, Integer taxYear, TaxBill.Type billType);

	boolean existsByAssessmentIdAndStatusNot(Long assessmentId, TaxBill.Status status);

	boolean existsByTaxRateIdAndStatusNot(Long taxRateId, TaxBill.Status status);

	/** Row lock: serializes payments/reversals/cancellation on one bill. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from TaxBill b where b.id = :id")
	Optional<TaxBill> findByIdForUpdate(Long id);

	@Query("""
			select coalesce(sum(p.amount), 0) from Payment p
			where p.taxBill.id = :billId and p.status = com.propertytax.payment.Payment.Status.RECORDED
			""")
	BigDecimal paidAmount(Long billId);

	@Query("""
			select new com.propertytax.taxbill.TaxBillRepository$PaidTotal(p.taxBill.id, sum(p.amount)) from Payment p
			where p.taxBill.taxAccount.id = :accountId and p.status = com.propertytax.payment.Payment.Status.RECORDED
			group by p.taxBill.id
			""")
	List<PaidTotal> paidTotalsForAccount(Long accountId);

	@Query("""
			select coalesce(sum(b.amount), 0) from TaxBill b
			where b.taxAccount.id = :accountId and b.status <> com.propertytax.taxbill.TaxBill.Status.CANCELLED
			""")
	BigDecimal totalBilled(Long accountId);

	default TaxBill require(Long id) {
		return findById(id).orElseThrow(() -> ApiErrors.notFound("Tax bill", id));
	}

	default TaxBill requireForUpdate(Long id) {
		return findByIdForUpdate(id).orElseThrow(() -> ApiErrors.notFound("Tax bill", id));
	}

}
