package com.karaoke.lounge.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.karaoke.lounge.entity.Member;

import jakarta.persistence.LockModeType;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByMemberNo(String memberNo);

    boolean existsByPhone(String phone);

    List<Member> findByLevel(String level);

    /** 储值/扣款时锁住行，避免并发结账把余额扣成负数或后账快照错乱 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :id")
    Optional<Member> findWithPessimisticLockById(@Param("id") Long id);
}
