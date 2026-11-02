package com.psymate.linking.application;

import com.psymate.clinical.api.EntryFacade;
import com.psymate.clinical.api.EntrySummary;
import com.psymate.linking.domain.GraphRequestException;
import com.psymate.linking.domain.GraphRequestException.Problem;
import com.psymate.linking.domain.Link;
import com.psymate.linking.domain.LinkError;
import com.psymate.linking.domain.LinkException;
import com.psymate.linking.persistence.GraphRepository;
import com.psymate.patients.api.PatientOwnershipGuard;
import com.psymate.vocabulary.api.LinkTypeCatalog;
import com.psymate.vocabulary.api.LinkTypeView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Graphe d'un patient (A4.4, A6, UC-08) : voisinage d'une entrée par CTE récursive sur link (ou
 * entrées les plus liées pour la racine « patient »), enrichi par la lecture par lot de clinical,
 * puis niveau de détail (150 nœuds, super-nœuds). Un graphe ne contient que des liens acceptés et
 * des entrées non archivées du patient. Gardes dans l'ordre : propriété du patient (403 / 404),
 * paramètres (422), racine (404, 422, 409).
 */
@Service
@Transactional(readOnly = true)
public class GraphQueryService {

  /** Nombre maximal de nœuds rendus ; au-delà, regroupement puis troncature. */
  public static final int MAX_NODES = 150;

  /** Profondeur maximale d'un voisinage. */
  public static final int MAX_DEPTH = 3;

  /** Nombre maximal de membres d'un expand par liste. */
  public static final int MAX_MEMBER_IDS = 500;

  private static final Pattern SUPER_NODE =
      Pattern.compile("^sn:([0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}):([a-z_]+)$");

  private final GraphRepository graph;
  private final EntryFacade entries;
  private final LinkTypeCatalog catalog;
  private final PatientOwnershipGuard guard;

  GraphQueryService(
      GraphRepository graph,
      EntryFacade entries,
      LinkTypeCatalog catalog,
      PatientOwnershipGuard guard) {
    this.graph = graph;
    this.entries = entries;
    this.catalog = catalog;
    this.guard = guard;
  }

  // ---- voisinage

  /**
   * Voisinage de la racine à la profondeur demandée (A4.4), limité à 150 nœuds : au-delà, les nœuds
   * de degré 1 d'un même type reliés au même voisin sont regroupés en super-nœuds. Seuls les liens
   * acceptés comptent ; les entrées archivées n'apparaissent pas.
   *
   * @throws com.psymate.patients.api.PatientAccessException 403 si le patient est d'un autre
   *     praticien, 404 s'il n'existe pas
   * @throws GraphRequestException 422 si la profondeur, un type de lien ou la période est invalide
   * @throws LinkException ENTRY_NOT_FOUND (404, racine inconnue ou d'un autre praticien),
   *     DIFFERENT_PATIENT (422, racine d'un autre patient du même praticien), ENTRY_ARCHIVED (409)
   */
  public GraphView neighborhood(GraphQuery query) {
    UUID patientId = query.patientId();
    guard.requireOwned(patientId);
    List<String> types = validate(query);
    UUID root = query.rootId();
    if (root != null) {
      requireUsableRoot(root, patientId);
    }

    Map<UUID, Integer> distances = new LinkedHashMap<>();
    boolean limited = false;
    if (root != null) {
      distances.putAll(graph.neighborhood(patientId, root, query.depth(), types));
    } else {
      // Les plus liées au sens des types demandés ; la période et l'archivage se filtrent après
      // la lecture par lot (le graphe ne lit pas entry), donc un filtre de période très étroit
      // peut rendre moins de 150 nœuds qu'il n'en existe.
      List<UUID> linked = graph.mostLinkedEntries(patientId, MAX_NODES + 1, types);
      limited = linked.size() > MAX_NODES;
      linked.stream().limit(MAX_NODES).forEach(id -> distances.put(id, 1));
    }

    Map<UUID, EntrySummary> summaries = entries.summariesOf(distances.keySet());
    List<UUID> ids = new ArrayList<>();
    for (UUID id : distances.keySet()) {
      EntrySummary s = summaries.get(id);
      if (!isLive(s, patientId)) {
        continue;
      }
      if (id.equals(root) || inPeriod(s, query.from(), query.to())) {
        ids.add(id);
      }
    }

    List<GraphEdge> edges =
        graph.acceptedEdgesAmong(ids, types).stream().map(GraphQueryService::edge).toList();
    Map<UUID, Integer> degrees = graph.acceptedDegrees(ids);
    List<GraphNode> nodes =
        ids.stream()
            .map(id -> node(summaries.get(id), degrees.getOrDefault(id, 0), distances.get(id)))
            .toList();
    GraphView full = new GraphView(GraphGrouping.detailOrder(nodes), edges, List.of(), limited);
    return GraphGrouping.apply(full, root, MAX_NODES);
  }

  /** Paramètres du voisinage ; renvoie les types de liens normalisés (vide = tous). */
  private List<String> validate(GraphQuery query) {
    List<Problem> problems = new ArrayList<>();
    if (query.depth() < 1 || query.depth() > MAX_DEPTH) {
      problems.add(new Problem("depth", "OUT_OF_RANGE"));
    }
    if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
      problems.add(new Problem("from", "INVALID_TIME"));
    }
    List<String> types = new ArrayList<>();
    if (query.linkTypes() != null && !query.linkTypes().isEmpty()) {
      Set<String> known = new HashSet<>();
      catalog.activeLinkTypes().stream().map(LinkTypeView::code).forEach(known::add);
      boolean unknown = false;
      for (String raw : query.linkTypes()) {
        String code = raw == null ? "" : raw.strip();
        if (code.isEmpty()) {
          continue;
        }
        if (known.contains(code)) {
          types.add(code);
        } else {
          unknown = true;
        }
      }
      if (unknown) {
        problems.add(new Problem("linkTypes", "INVALID_VALUE"));
      }
    }
    if (!problems.isEmpty()) {
      throw new GraphRequestException(problems);
    }
    return types.stream().distinct().toList();
  }

  /**
   * La racine est une entrée du patient : 404 si elle n'existe pas ou est d'un autre praticien (on
   * ne révèle jamais une entrée qui n'est pas la sienne, ADR-09), 422 DIFFERENT_PATIENT si elle est
   * d'un autre patient du praticien, 409 si elle est archivée.
   */
  private void requireUsableRoot(UUID root, UUID patientId) {
    if (!entries.belongsToPatient(root, patientId)) {
      if (entries.exists(root)) {
        throw LinkException.atField(
            LinkError.DIFFERENT_PATIENT, "root", "La racine appartient à un autre patient");
      }
      throw new LinkException(LinkError.ENTRY_NOT_FOUND, "Entrée introuvable");
    }
    if (entries.isArchived(root)) {
      throw LinkException.archived("root");
    }
  }

  // ---- expand

  /**
   * Développe un groupe : les nœuds du groupe et tous les liens acceptés qui les touchent. Le
   * groupe est désigné soit par l'identifiant d'un super-nœud ({@code superNodeId}), soit par la
   * liste de ses membres ({@code memberIds}) : exactement l'un des deux. Un super-nœud se développe
   * sans les filtres (types de liens, période) du graphe qui l'a produit : ses membres sont toutes
   * les entrées non archivées du type, reliées au voisin par leur seul lien accepté ; au-delà de
   * 500 membres la réponse est tronquée ({@code truncated = true}). Pour un développement exact, le
   * client envoie les {@code memberIds} reçus avec le super-nœud.
   *
   * @throws GraphRequestException 422 si les paramètres sont invalides ou si un membre n'est pas
   *     une entrée non archivée du patient
   * @throws LinkException SUPER_NODE_NOT_FOUND (404) ; garde de propriété comme {@link
   *     #neighborhood}
   */
  public GraphView expand(UUID patientId, String superNodeId, List<UUID> memberIds) {
    guard.requireOwned(patientId);
    boolean hasSuperNode = superNodeId != null && !superNodeId.isBlank();
    if (hasSuperNode == (memberIds != null)) {
      throw new GraphRequestException("superNodeId", "INVALID_VALUE");
    }
    List<GraphNode> members =
        hasSuperNode
            ? membersOfSuperNode(patientId, superNodeId)
            : membersOfList(patientId, memberIds);
    // Un super-nœud se développe sans les filtres du graphe d'origine (types, période) : il peut
    // compter plus de membres que le count rendu ; au-delà de MAX_MEMBER_IDS on tronque.
    boolean truncated = members.size() > MAX_MEMBER_IDS;
    if (truncated) {
      members = members.subList(0, MAX_MEMBER_IDS);
    }
    List<UUID> ids = members.stream().map(GraphNode::id).toList();

    List<Link> touching = graph.acceptedEdgesTouching(ids);
    Set<UUID> endpoints = new LinkedHashSet<>();
    touching.forEach(
        l -> {
          endpoints.add(l.sourceId());
          endpoints.add(l.targetId());
        });
    Map<UUID, EntrySummary> summaries = entries.summariesOf(endpoints);
    List<GraphEdge> edges =
        touching.stream()
            .filter(
                l ->
                    isLive(summaries.get(l.sourceId()), patientId)
                        && isLive(summaries.get(l.targetId()), patientId))
            .map(GraphQueryService::edge)
            .toList();
    return new GraphView(GraphGrouping.detailOrder(members), edges, List.of(), truncated);
  }

  private List<GraphNode> membersOfSuperNode(UUID patientId, String superNodeId) {
    Matcher m = SUPER_NODE.matcher(superNodeId.strip());
    if (!m.matches()) {
      throw new GraphRequestException("superNodeId", "INVALID_FORMAT");
    }
    UUID hub = UUID.fromString(m.group(1));
    String type = m.group(2);
    EntrySummary hubSummary = entries.summariesOf(List.of(hub)).get(hub);
    if (!isLive(hubSummary, patientId)) {
      throw superNodeNotFound();
    }
    List<UUID> candidates = graph.degreeOneNeighborsOf(hub);
    Map<UUID, EntrySummary> summaries = entries.summariesOf(candidates);
    List<UUID> ids =
        candidates.stream()
            .filter(id -> isLive(summaries.get(id), patientId))
            .filter(id -> type.equals(summaries.get(id).type()))
            .toList();
    if (ids.isEmpty()) {
      throw superNodeNotFound();
    }
    return nodesOf(ids, summaries);
  }

  private List<GraphNode> membersOfList(UUID patientId, List<UUID> memberIds) {
    if (memberIds.isEmpty()) {
      throw new GraphRequestException("memberIds", "REQUIRED");
    }
    if (memberIds.size() > MAX_MEMBER_IDS) {
      throw new GraphRequestException("memberIds", "OUT_OF_RANGE");
    }
    if (memberIds.stream().anyMatch(Objects::isNull)
        || new HashSet<>(memberIds).size() != memberIds.size()) {
      throw new GraphRequestException("memberIds", "INVALID_VALUE");
    }
    Map<UUID, EntrySummary> summaries = entries.summariesOf(memberIds);
    if (!memberIds.stream().allMatch(id -> isLive(summaries.get(id), patientId))) {
      throw new GraphRequestException("memberIds", "ENTRY_NOT_FOUND");
    }
    return nodesOf(memberIds, summaries);
  }

  /** Nœuds à la distance 1 d'un groupe, avec leur degré dans tout le dossier. */
  private List<GraphNode> nodesOf(List<UUID> ids, Map<UUID, EntrySummary> summaries) {
    Map<UUID, Integer> degrees = graph.acceptedDegrees(ids);
    return ids.stream().map(id -> node(summaries.get(id), degrees.getOrDefault(id, 0), 1)).toList();
  }

  private static LinkException superNodeNotFound() {
    return new LinkException(LinkError.SUPER_NODE_NOT_FOUND, "Groupe introuvable");
  }

  // ---- éléments

  /** L'entrée existe, est du patient et n'est pas archivée. */
  private static boolean isLive(EntrySummary s, UUID patientId) {
    return s != null && !s.archived() && patientId.equals(s.patientId());
  }

  private static boolean inPeriod(EntrySummary s, Instant from, Instant to) {
    Instant at = s.occurredFrom();
    if (from == null && to == null) {
      return true;
    }
    // Même convention que la timeline et la recherche : from inclus, to exclu.
    return at != null && (from == null || !at.isBefore(from)) && (to == null || at.isBefore(to));
  }

  private static GraphNode node(EntrySummary s, int degree, int distance) {
    return new GraphNode(
        s.id(),
        s.type(),
        s.title(),
        s.occurredFrom(),
        s.intensity(),
        s.emotionCode(),
        degree,
        distance,
        s.authorType());
  }

  private static GraphEdge edge(Link l) {
    return new GraphEdge(
        l.id(),
        l.sourceId(),
        l.targetId(),
        l.linkType(),
        l.directed(),
        l.provenance().code(),
        l.status().code());
  }
}
