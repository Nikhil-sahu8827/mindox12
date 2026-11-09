package com.substring.docmind.document.service;

import com.substring.docmind.document.dto.DocumentDto;
import com.substring.docmind.document.dto.IngestUrlRequest;
import com.substring.docmind.security.UserPrincipal;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {

    DocumentDto uploadDocument(MultipartFile file, UserPrincipal currentUser);

    List<DocumentDto> uploadMultipleDocuments(List<MultipartFile> files, UserPrincipal currentUser);

    DocumentDto ingestUrl(IngestUrlRequest request, UserPrincipal currentUser);

    DocumentDto ingestYouTube(String url, UserPrincipal currentUser);

    List<DocumentDto> getUserDocuments(UserPrincipal currentUser);

    List<DocumentDto> getAllDocuments(UserPrincipal currentUser);

    DocumentDto getDocumentById(String documentId, UserPrincipal currentUser);

    DocumentDto updateDocument(String documentId, MultipartFile file, UserPrincipal currentUser);

    void deleteDocument(String documentId, UserPrincipal currentUser);
}
