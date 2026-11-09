package com.substring.docmind.document;

import com.substring.docmind.document.dto.DocumentDto;
import com.substring.docmind.document.entity.DocumentMetadata;
import com.substring.docmind.document.mapper.DocumentMapper;
import com.substring.docmind.document.repository.DocumentMetadataRepository;
import com.substring.docmind.document.service.DocumentIngestionService;
import com.substring.docmind.document.service.DocumentParserService;
import com.substring.docmind.document.service.DocumentServiceImpl;
import com.substring.docmind.enums.DocumentStatus;
import com.substring.docmind.enums.Role;
import com.substring.docmind.exception.ResourceNotFoundException;
import com.substring.docmind.security.UserPrincipal;
import com.substring.docmind.user.entity.User;
import com.substring.docmind.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentMetadataRepository documentMetadataRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DocumentParserService parserService;

    @Mock
    private DocumentIngestionService ingestionService;

    @Mock
    private DocumentMapper documentMapper;

    @InjectMocks
    private DocumentServiceImpl documentService;

    private User sampleUser;
    private UserPrincipal samplePrincipal;
    private DocumentMetadata sampleDoc;
    private DocumentDto sampleDocDto;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .role(Role.ROLE_USER)
                .build();

        samplePrincipal = UserPrincipal.create(sampleUser);

        sampleDoc = DocumentMetadata.builder()
                .id("doc_123")
                .user(sampleUser)
                .filename("test.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .status(DocumentStatus.PROCESSING)
                .build();

        sampleDocDto = DocumentDto.builder()
                .id("doc_123")
                .filename("test.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .status(DocumentStatus.PROCESSING)
                .build();
    }

    @Test
    void uploadDocument_Success() {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "sample data".getBytes());

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(documentMetadataRepository.save(any(DocumentMetadata.class))).thenReturn(sampleDoc);
        when(parserService.extractText(file)).thenReturn("sample data");
        when(parserService.chunkText(anyString(), anyString(), anyLong(), anyString(), anyString())).thenReturn(Collections.emptyList());
        when(documentMapper.toDto(sampleDoc)).thenReturn(sampleDocDto);

        DocumentDto result = documentService.uploadDocument(file, samplePrincipal);

        assertNotNull(result);
        assertEquals("doc_123", result.getId());
        verify(ingestionService, times(1)).ingestChunksAsync(anyString(), anyList());
    }

    @Test
    void getUserDocuments_ReturnsOnlyCurrentUserDocuments() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(documentMetadataRepository.findByUserOrderByCreatedAtDesc(sampleUser)).thenReturn(List.of(sampleDoc));
        when(documentMapper.toDto(sampleDoc)).thenReturn(sampleDocDto);

        List<DocumentDto> docs = documentService.getUserDocuments(samplePrincipal);

        assertNotNull(docs);
        assertEquals(1, docs.size());
        assertEquals("doc_123", docs.get(0).getId());
    }

    @Test
    void deleteDocument_AnotherUserDocument_ThrowsResourceNotFoundException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(documentMetadataRepository.findByIdAndUser("foreign_doc", sampleUser)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> documentService.deleteDocument("foreign_doc", samplePrincipal));
        verify(documentMetadataRepository, never()).delete(any());
    }
}
