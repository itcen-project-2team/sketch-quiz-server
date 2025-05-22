package itcen.whiteboardserver.member.application.controller;

import io.swagger.v3.oas.annotations.Operation;
import itcen.whiteboardserver.member.application.dto.MemberDTO;
import itcen.whiteboardserver.member.application.service.MemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/member")
public class MemberController {

    private final MemberService memberService;

    @Autowired
    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "멤버 조회",
            description =
                    """
                    멤버 id를 기반으로 멤버 정보를 조회해옵니다.\n
                    후에는 JWT 토큰을 통해 멤버 정보를 조회할 것입니다.
                    """
    )
    public ResponseEntity<MemberDTO> getMember(@PathVariable("id") Long id) {
        MemberDTO member = memberService.getMemberById(id);
        return ResponseEntity.ok()
                .body(member);
    }
}
