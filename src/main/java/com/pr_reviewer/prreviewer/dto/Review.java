package com.pr_reviewer.prreviewer.dto;

import com.networknt.schema.utils.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
//import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String repoFullName;
    private int prNumber;

    @Column(columnDefinition = "TEXT")
    private String summary;

    private int filesIncludedInFull;
    private int filesOverflowed;
    private int filesDeleted;
    private int retrievedChunkCount;
    private int approxTokensUsed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<RetrievedChunkInfo> retrievedChunks;

    private Instant createdAt;

    public record RetrievedChunkInfo(
            String filename,
            String snippet,
            Double score
    ) {}
}
