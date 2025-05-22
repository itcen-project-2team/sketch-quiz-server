package itcen.whiteboardserver.member.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MemberDTO {
    @Schema(description = "회원 ID", example = "1")
    private Long id;
    @Schema(description = "회원 이름", example = "aa")
    private String name;
    @Schema(description = "회원 email", example = "aa@gmail.com")
    private String email;
}
