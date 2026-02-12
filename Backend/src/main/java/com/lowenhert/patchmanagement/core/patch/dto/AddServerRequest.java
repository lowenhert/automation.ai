package com.lowenhert.patchmanagement.core.patch.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddServerRequest {
    private String hostname;
    private String ip;
    private String osType;
    private String sshUser;
    private Integer sshPort;
    private String ansibleGroup;
    private Boolean active;
}
