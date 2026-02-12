package com.lowenhert.patchmanagement.core.patch.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Server {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String hostname;

    private String ip;

    private String osType;

    private String sshUser;

    private Integer sshPort;

    private String ansibleGroup;

    private LocalDateTime addedOn;

    private Boolean active;
}
