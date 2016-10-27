package de.digitalcollections.core.business.api;

import de.digitalcollections.core.model.api.resource.Resource;
import de.digitalcollections.core.model.api.resource.enums.ResourcePersistenceType;
import de.digitalcollections.core.model.api.resource.exceptions.ResourceIOException;
import java.io.InputStream;
import java.net.URI;

public interface ResourceService {

  Resource create(String key, ResourcePersistenceType resourcePersistenceType, String filenameExtension) throws ResourceIOException;

  Resource get(String key, ResourcePersistenceType resourcePersistenceType, String filenameExtension) throws ResourceIOException;

  InputStream getInputStream(Resource resource) throws ResourceIOException;

  InputStream getInputStream(URI resourceUri) throws ResourceIOException;

  void write(Resource resource, String output) throws ResourceIOException;
}
