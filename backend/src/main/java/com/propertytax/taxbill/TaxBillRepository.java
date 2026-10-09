package com.propertytax.taxbill;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxBillRepository extends JpaRepository<TaxBill, Long> {
}
