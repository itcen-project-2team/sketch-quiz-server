package itcen.whiteboardserver.member.application.service;

import itcen.whiteboardserver.member.application.dto.MemberDTO;

public interface MemberService {
    // Define the methods that will be implemented in the service class
    MemberDTO getMemberById(Long id);

    // Add other member-related methods as needed
}
