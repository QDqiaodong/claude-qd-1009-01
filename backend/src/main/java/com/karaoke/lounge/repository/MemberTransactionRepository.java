package com.karaoke.lounge.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.karaoke.lounge.entity.MemberTransaction;

public interface MemberTransactionRepository extends JpaRepository<MemberTransaction, Long> {

    List<MemberTransaction> findByMemberIdOrderByCreatedAtDescIdDesc(Long memberId);
}
