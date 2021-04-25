package com.github.eltonsandre.plugin.idea.spring.assistant.gotodeclaration;

import com.github.eltonsandre.plugin.idea.spring.assistant.common.Constants;
import com.github.eltonsandre.plugin.idea.spring.assistant.common.annotation.SpringAnnotationEnum;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLFileType;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.impl.YAMLKeyValueImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LinkGoToFilePropertieKey {

    private static final Logger log = Logger.getInstance(LinkGoToFilePropertieKey.class);

    public static PsiElement[] toPropertieKey(@NotNull final PsiElement sourceElement) {
        final IElementType tokenType = ((PsiJavaToken) sourceElement).getTokenType();
        if (tokenType != JavaTokenType.STRING_LITERAL) {
            return new PsiElement[0];
        }

        final var psiAnnotation = PsiTreeUtil.getParentOfType(sourceElement, PsiAnnotation.class);
        if (psiAnnotation == null) {
            log.debug("no annotation");
            return new PsiElement[0];
        }

        final var annotationName = psiAnnotation.getQualifiedName();
        if (SpringAnnotationEnum.notContains(annotationName) || annotationName == null) {
            log.debug("no spring annotation");
            return new PsiElement[0];
        }

        final var project = sourceElement.getProject();
        final Collection<VirtualFile> files = FileTypeIndex.getFiles(YAMLFileType.YML, GlobalSearchScope.projectScope(project));
        files.addAll(FileTypeIndex.getFiles(PropertiesFileType.INSTANCE, GlobalSearchScope.projectScope(project)));

        if (CollectionUtils.isEmpty(files)) {
            return new PsiElement[0];
        }

        final var instance = PsiManager.getInstance(sourceElement.getProject());
        final String key = qualifiedKey(SpringAnnotationEnum.fromQualifiedName(annotationName).isHasPlaceholder(),
                sourceElement.getText());

        final List<PsiElement> result = new ArrayList<>(files.size());

        files.forEach(file -> {
            final PsiFile psiFile = Objects.requireNonNull(instance.findFile(file));

            if (YAMLFileType.YML.equals(psiFile.getFileType())) {
                final var yamlFile = (YAMLFile) psiFile;
                yamlFile.getDocuments().forEach(yamlDocument -> {
                    final YAMLKeyValue yamlKeyValue = YAMLUtil.getQualifiedKeyInDocument(yamlDocument, List.of(key.split(Constants.REGEX_DOT)));
                    if (Objects.nonNull(yamlKeyValue)) {
                        result.add(new YAMLKeyValueImpl(yamlKeyValue.getNode()));
                    }
                });
            }

            if (PropertiesFileType.INSTANCE.getLanguage().getID().equals(psiFile.getFileType().getName())) {
                log.debug("PropertiesFileType " + psiFile);
                final PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(psiFile);

                final IProperty propertyByKey;
                if (Objects.nonNull(propertiesFile)) {
                    propertyByKey = propertiesFile.findPropertyByKey(key);
                    if (Objects.nonNull(propertyByKey)) {
                        result.add(new PropertyNavigationItem(propertyByKey.getPsiElement().getNode()));
                    }
                }
            }

        });

        final var psiElements = new PsiElement[result.size()];
        return result.toArray(psiElements);
    }


    private static String qualifiedKey(final boolean isHasPlaceholder, final String key) {
        if (isHasPlaceholder) {
            return LinkUtils.getKeyToPlaceholder(key);
        } else {
            return key.substring(1, key.length() - 1);
        }
    }

}
