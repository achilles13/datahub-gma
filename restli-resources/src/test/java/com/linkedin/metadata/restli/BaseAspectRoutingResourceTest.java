package com.linkedin.metadata.restli;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.linkedin.common.urn.Urn;
import com.linkedin.data.template.RecordTemplate;
import com.linkedin.metadata.dao.AspectKey;
import com.linkedin.metadata.dao.BaseBrowseDAO;
import com.linkedin.metadata.dao.BaseLocalDAO;
import com.linkedin.metadata.dao.BaseSearchDAO;
import com.linkedin.metadata.dao.utils.ModelUtils;
import com.linkedin.metadata.dao.utils.RecordUtils;
import com.linkedin.parseq.BaseEngineTest;
import com.linkedin.restli.common.ComplexResourceKey;
import com.linkedin.restli.common.EmptyRecord;
import com.linkedin.restli.server.ResourceContext;
import com.linkedin.testing.AspectBar;
import com.linkedin.testing.AspectFoo;
import com.linkedin.testing.EntityAspectUnion;
import com.linkedin.testing.EntityAspectUnionArray;
import com.linkedin.testing.EntityDocument;
import com.linkedin.testing.EntityKey;
import com.linkedin.testing.EntitySnapshot;
import com.linkedin.testing.EntityValue;
import com.linkedin.testing.urn.FooUrn;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.linkedin.metadata.dao.BaseReadDAO.*;
import static com.linkedin.testing.TestUtils.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;


public class BaseAspectRoutingResourceTest extends BaseEngineTest {
  private BaseBrowseDAO _mockBrowseDAO;
  private BaseLocalDAO _mockLocalDAO;
  private BaseAspectRoutingGmsClient _mockGmsClient;
  private BaseAspectRoutingResourceTest.TestResource _resource = new BaseAspectRoutingResourceTest.TestResource();

  class TestResource extends BaseAspectRoutingResource<
      // format
      ComplexResourceKey<EntityKey, EmptyRecord>, EntityValue, Urn, EntitySnapshot, EntityAspectUnion, EntityDocument, AspectFoo> {

    public TestResource() {
      super(EntitySnapshot.class, EntityAspectUnion.class, AspectFoo.class, EntityValue.class);
    }

    @Nonnull
    @Override
    protected BaseLocalDAO<EntityAspectUnion, Urn> getLocalDAO() {
      return _mockLocalDAO;
    }

    @Nonnull
    @Override
    protected BaseSearchDAO getSearchDAO() {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Nonnull
    @Override
    protected BaseBrowseDAO getBrowseDAO() {
      return _mockBrowseDAO;
    }

    @Nonnull
    @Override
    protected Urn createUrnFromString(@Nonnull String urnString) {
      try {
        return Urn.createFromString(urnString);
      } catch (URISyntaxException e) {
        throw RestliUtils.badRequestException("Invalid URN: " + urnString);
      }
    }

    @Nonnull
    @Override
    protected FooUrn toUrn(@Nonnull ComplexResourceKey<EntityKey, EmptyRecord> key) {
      return makeFooUrn(key.getKey().getId().intValue());
    }

    @Nonnull
    @Override
    protected ComplexResourceKey<EntityKey, EmptyRecord> toKey(@Nonnull Urn urn) {
      return new ComplexResourceKey<>(new EntityKey().setId(urn.getIdAsLong()), new EmptyRecord());
    }

    @Nonnull
    @Override
    protected EntityValue toValue(@Nonnull EntitySnapshot snapshot) {
      EntityValue value = new EntityValue();
      ModelUtils.getAspectsFromSnapshot(snapshot).forEach(a -> {
        if (a instanceof AspectFoo) {
          value.setFoo(AspectFoo.class.cast(a));
        } else if (a instanceof AspectBar) {
          value.setBar(AspectBar.class.cast(a));
        }
      });
      return value;
    }

    @Nonnull
    @Override
    protected EntitySnapshot toSnapshot(@Nonnull EntityValue value, @Nonnull Urn urn) {
      EntitySnapshot snapshot = new EntitySnapshot().setUrn(urn);
      EntityAspectUnionArray aspects = new EntityAspectUnionArray();
      if (value.hasFoo()) {
        aspects.add(ModelUtils.newAspectUnion(EntityAspectUnion.class, value.getFoo()));
      }
      if (value.hasBar()) {
        aspects.add(ModelUtils.newAspectUnion(EntityAspectUnion.class, value.getBar()));
      }

      snapshot.setAspects(aspects);
      return snapshot;
    }

    @Nonnull
    @Override
    public String getRoutingAspectFieldName() {
      return "Foo";
    }

    @Nonnull
    @Override
    public BaseAspectRoutingGmsClient getGmsClient() {
      return _mockGmsClient;
    }

    @Override
    public ResourceContext getContext() {
      return mock(ResourceContext.class);
    }
  }

  @BeforeMethod
  public void setup() {
    _mockGmsClient = mock(BaseAspectRoutingGmsClient.class);
    _mockLocalDAO = mock(BaseLocalDAO.class);
  }

  @Test
  public void testGetWithRoutingAspect() {
    FooUrn urn = makeFooUrn(1234);
    AspectFoo foo = new AspectFoo().setValue("foo");
    AspectBar bar = new AspectBar().setValue("bar");

    AspectKey<FooUrn, AspectBar> aspectBarKey = new AspectKey<>(AspectBar.class, urn, LATEST_VERSION);

    when(_mockLocalDAO.exists(urn)).thenReturn(true);
    when(_mockLocalDAO.get(new HashSet<>(Arrays.asList(aspectBarKey)))).thenReturn(
        Collections.singletonMap(aspectBarKey, Optional.of(bar)));
    when(_mockGmsClient.get(makeResourceKey(urn))).thenReturn(foo);

    EntityValue value = runAndWait(_resource.get(makeResourceKey(urn), new String[]{AspectFoo.class.getCanonicalName(), AspectBar.class.getCanonicalName()}));

    assertTrue(value.hasFoo());
    assertEquals(value.getFoo(), foo);

    assertTrue(value.hasBar());
    assertEquals(value.getBar(), bar);
  }

  @Test
  public void testGetWithoutRoutingAspect() {
    FooUrn urn = makeFooUrn(1234);
    AspectBar bar = new AspectBar().setValue("bar");

    AspectKey<FooUrn, AspectBar> aspectBarKey = new AspectKey<>(AspectBar.class, urn, LATEST_VERSION);

    when(_mockLocalDAO.exists(urn)).thenReturn(true);
    when(_mockLocalDAO.get(new HashSet<>(Arrays.asList(aspectBarKey)))).thenReturn(
        Collections.singletonMap(aspectBarKey, Optional.of(bar)));

    EntityValue value = runAndWait(_resource.get(makeResourceKey(urn), new String[]{AspectBar.class.getCanonicalName()}));

    assertFalse(value.hasFoo());
    verifyZeroInteractions(_mockGmsClient);

    assertTrue(value.hasBar());
    assertEquals(value.getBar(), bar);
  }

  @Test
  public void testGetWithOnlyRoutingAspect() {
    FooUrn urn = makeFooUrn(1234);
    AspectFoo foo = new AspectFoo().setValue("foo");

    when(_mockLocalDAO.exists(urn)).thenReturn(true);
    when(_mockGmsClient.get(makeResourceKey(urn))).thenReturn(foo);

    EntityValue value = runAndWait(_resource.get(makeResourceKey(urn), new String[]{AspectFoo.class.getCanonicalName()}));

    assertTrue(value.hasFoo());
    assertEquals(value.getFoo(), foo);

    assertFalse(value.hasBar());
    verify(_mockLocalDAO, times(0)).get(anySet());
  }

  @Test
  public void testGetWithEmptyValueFromLocalDao() {
    FooUrn urn = makeFooUrn(1234);
    AspectFoo foo = new AspectFoo().setValue("foo");

    AspectKey<FooUrn, AspectBar> aspectBarKey = new AspectKey<>(AspectBar.class, urn, LATEST_VERSION);

    when(_mockLocalDAO.exists(urn)).thenReturn(true);
    when(_mockLocalDAO.get(new HashSet<>(Arrays.asList(aspectBarKey)))).thenReturn(
        Collections.singletonMap(aspectBarKey, Optional.empty()));
    when(_mockGmsClient.get(makeResourceKey(urn))).thenReturn(foo);

    EntityValue value = runAndWait(_resource.get(makeResourceKey(urn), new String[]{AspectFoo.class.getCanonicalName(), AspectBar.class.getCanonicalName()}));

    assertTrue(value.hasFoo());
    assertEquals(value.getFoo(), foo);
    assertFalse(value.hasBar());
  }

  @Test
  public void testGetWithNullValueFromGms() {
    FooUrn urn = makeFooUrn(1234);
    AspectBar bar = new AspectBar().setValue("bar");

    AspectKey<FooUrn, AspectBar> aspectBarKey = new AspectKey<>(AspectBar.class, urn, LATEST_VERSION);

    when(_mockLocalDAO.exists(urn)).thenReturn(true);
    when(_mockLocalDAO.get(new HashSet<>(Arrays.asList(aspectBarKey)))).thenReturn(
        Collections.singletonMap(aspectBarKey, Optional.of(bar)));
    when(_mockGmsClient.get(makeResourceKey(urn))).thenReturn(null);

    EntityValue value = runAndWait(_resource.get(makeResourceKey(urn), new String[]{AspectFoo.class.getCanonicalName(), AspectBar.class.getCanonicalName()}));

    assertTrue(value.hasBar());
    assertEquals(value.getBar(), bar);
    assertFalse(value.hasFoo());
  }

  @Test
  public void testIngestWithRoutingAspect() {
    FooUrn urn = makeFooUrn(1);
    AspectFoo foo = new AspectFoo().setValue("foo");
    AspectBar bar = new AspectBar().setValue("bar");
    List<EntityAspectUnion> aspects = Arrays.asList(ModelUtils.newAspectUnion(EntityAspectUnion.class, foo),
        ModelUtils.newAspectUnion(EntityAspectUnion.class, bar));
    EntitySnapshot snapshot = ModelUtils.newSnapshot(EntitySnapshot.class, urn, aspects);

    runAndWait(_resource.ingest(snapshot));

    verify(_mockLocalDAO, times(1)).add(eq(urn), eq(bar), any());
    verify(_mockGmsClient, times(1)).ingest(eq(_resource.toKey(urn)), eq(foo));
    verifyNoMoreInteractions(_mockLocalDAO);
  }

  @Test
  public void testIngestWithoutRoutingAspect() {
    FooUrn urn = makeFooUrn(1);
    AspectBar bar = new AspectBar().setValue("bar");
    List<EntityAspectUnion> aspects = Arrays.asList(ModelUtils.newAspectUnion(EntityAspectUnion.class, bar));
    EntitySnapshot snapshot = ModelUtils.newSnapshot(EntitySnapshot.class, urn, aspects);

    runAndWait(_resource.ingest(snapshot));

    verify(_mockLocalDAO, times(1)).add(eq(urn), eq(bar), any());
    verifyZeroInteractions(_mockGmsClient);
    verifyNoMoreInteractions(_mockLocalDAO);
  }

  @Test
  public void testIngestWithOnlyRoutingAspect() {
    FooUrn urn = makeFooUrn(1);
    AspectFoo foo = new AspectFoo().setValue("foo");
    List<EntityAspectUnion> aspects = Arrays.asList(ModelUtils.newAspectUnion(EntityAspectUnion.class, foo));
    EntitySnapshot snapshot = ModelUtils.newSnapshot(EntitySnapshot.class, urn, aspects);

    runAndWait(_resource.ingest(snapshot));

    verifyZeroInteractions(_mockLocalDAO);
    verify(_mockGmsClient, times(1)).ingest(eq(_resource.toKey(urn)), eq(foo));
    verifyNoMoreInteractions(_mockGmsClient);
  }

  @Test
  public void testGetSnapshotWithoutRoutingAspect() {
    FooUrn urn = makeFooUrn(1);
    AspectFoo bar = new AspectFoo().setValue("bar");
    AspectKey<FooUrn, ? extends RecordTemplate> barKey = new AspectKey<>(AspectBar.class, urn, LATEST_VERSION);
    when(_mockLocalDAO.get(ImmutableSet.of(barKey))).thenReturn(ImmutableMap.of(barKey, Optional.of(bar)));

    EntitySnapshot snapshot = runAndWait(_resource.getSnapshot(urn.toString(), new String[]{AspectBar.class.getCanonicalName()}));

    assertEquals(snapshot.getUrn(), urn);
    assertEquals(snapshot.getAspects().size(), 1);
    Set<RecordTemplate> aspects =
        snapshot.getAspects().stream().map(RecordUtils::getSelectedRecordTemplateFromUnion).collect(Collectors.toSet());
    assertEquals(aspects, ImmutableSet.of(bar));
  }

  @Test
  public void testGetSnapshotWithRoutingAspect() {
    FooUrn urn = makeFooUrn(1);
    AspectFoo foo = new AspectFoo().setValue("foo");
    AspectFoo bar = new AspectFoo().setValue("bar");
    AspectKey<FooUrn, ? extends RecordTemplate> barKey = new AspectKey<>(AspectBar.class, urn, LATEST_VERSION);
    Set<AspectKey<FooUrn, ? extends RecordTemplate>> aspectKeys = ImmutableSet.of(barKey);
    when(_mockLocalDAO.get(aspectKeys)).thenReturn(ImmutableMap.of(barKey, Optional.of(bar)));
    when(_mockGmsClient.get(makeResourceKey(urn))).thenReturn(foo);

    EntitySnapshot snapshot = runAndWait(_resource.getSnapshot(urn.toString(),
        new String[]{AspectFoo.class.getCanonicalName(), AspectBar.class.getCanonicalName()}));

    assertEquals(snapshot.getUrn(), urn);
    Set<RecordTemplate> aspects =
        snapshot.getAspects().stream().map(RecordUtils::getSelectedRecordTemplateFromUnion).collect(Collectors.toSet());
    assertEquals(aspects, ImmutableSet.of(foo, bar));
  }

  @Test
  public void testGetSnapshotWithOnlyRoutingAspect() {
    FooUrn urn = makeFooUrn(1);
    AspectFoo foo = new AspectFoo().setValue("foo");
    when(_mockGmsClient.get(makeResourceKey(urn))).thenReturn(foo);

    EntitySnapshot snapshot = runAndWait(_resource.getSnapshot(urn.toString(), new String[]{AspectFoo.class.getCanonicalName()}));
    assertEquals(snapshot.getUrn(), urn);

    Set<RecordTemplate> aspects =
        snapshot.getAspects().stream().map(RecordUtils::getSelectedRecordTemplateFromUnion).collect(Collectors.toSet());

    assertEquals(snapshot.getUrn(), urn);
    assertEquals(aspects, ImmutableSet.of(foo));
    verifyZeroInteractions(_mockLocalDAO);
  }
}
