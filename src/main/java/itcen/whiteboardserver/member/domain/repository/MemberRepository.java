package itcen.whiteboardserver.member.domain.repository;

import itcen.whiteboardserver.member.domain.aggregate.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

}
