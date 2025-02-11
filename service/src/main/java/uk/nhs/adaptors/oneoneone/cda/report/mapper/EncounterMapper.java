package uk.nhs.adaptors.oneoneone.cda.report.mapper;

import static java.util.Arrays.stream;

import static org.hl7.fhir.dstu3.model.Encounter.EncounterStatus.FINISHED;
import static org.hl7.fhir.dstu3.model.Narrative.NarrativeStatus.GENERATED;

import static uk.nhs.adaptors.oneoneone.cda.report.enums.MessageHeaderEvent.DISCHARGE_DETAILS;
import static uk.nhs.adaptors.oneoneone.cda.report.enums.MessageHeaderEvent.REFERRAL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.Encounter.EncounterLocationComponent;
import org.hl7.fhir.dstu3.model.Encounter.EncounterParticipantComponent;
import org.hl7.fhir.dstu3.model.Group;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Narrative;
import org.hl7.fhir.dstu3.model.Organization;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Period;
import org.hl7.fhir.dstu3.model.PractitionerRole;
import org.hl7.fhir.dstu3.model.Reference;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import uk.nhs.adaptors.oneoneone.cda.report.service.AppointmentService;
import uk.nhs.adaptors.oneoneone.cda.report.util.NodeUtil;
import uk.nhs.adaptors.oneoneone.cda.report.util.ResourceUtil;
import uk.nhs.connect.iucds.cda.ucr.POCDMT000002UK01ClinicalDocument1;
import uk.nhs.connect.iucds.cda.ucr.POCDMT000002UK01Component3;
import uk.nhs.connect.iucds.cda.ucr.POCDMT000002UK01Encounter;
import uk.nhs.connect.iucds.cda.ucr.POCDMT000002UK01Entry;
import uk.nhs.connect.iucds.cda.ucr.POCDMT000002UK01Informant12;
import uk.nhs.connect.iucds.cda.ucr.POCDMT000002UK01PatientRole;
import uk.nhs.connect.iucds.cda.ucr.POCDMT000002UK01Section;
import uk.nhs.connect.iucds.cda.ucr.TS;

@Component
@AllArgsConstructor
public class EncounterMapper {

    private static final String PARTCIPANT_TYPE_CODE_REFT = "REFT";
    private static final String DIV_START = "<div>";
    private static final String DIV_END = "</div>";

    private static final String AUTHOR_PARTICIPANT_DISPLAY = "Author";
    private static final String AUTHOR_PARTICIPANT_CODE = "PPRF";
    private static final String RESPONSIBLE_PARTY_PARTICIPANT_DISPLAY = "Responsible Party";
    private static final String RESPONSIBLE_PARTY_PARTICIPANT_CODE = "ATND";
    private static final String PARTICIPANT_SYSTEM = "http://hl7.org/fhir/ValueSet/encounter-participant-type";

    private final PeriodMapper periodMapper;

    private final ParticipantMapper participantMapper;

    private final InformantMapper informantMapper;

    private final DataEntererMapper dataEntererMapper;

    private final ServiceProviderMapper serviceProviderMapper;

    private final LocationMapper locationMapper;

    private final AppointmentService appointmentService;

    private final PatientMapper patientMapper;

    private final GroupMapper groupMapper;

    private final NodeUtil nodeUtil;

    private final ResourceUtil resourceUtil;

    public Encounter mapEncounter(POCDMT000002UK01ClinicalDocument1 clinicalDocument, List<PractitionerRole> authorPractitionerRoles,
        Optional<PractitionerRole> responsibleParty, Coding interaction) {
        Encounter encounter = new Encounter();
        encounter.setIdElement(resourceUtil.newRandomUuid());
        encounter.setStatus(FINISHED);
        encounter.setLocation(getLocationComponents(clinicalDocument));
        encounter.setPeriod(getPeriod(clinicalDocument));
        setType(encounter, interaction);
        setServiceProvider(encounter, clinicalDocument);
        setIdentifiers(encounter, clinicalDocument);
        setSubject(encounter, clinicalDocument);
        encounter.setParticipant(getEncounterParticipantComponents(clinicalDocument, authorPractitionerRoles, responsibleParty, encounter));
        setAppointment(encounter, clinicalDocument);
        setEncounterReasonAndType(encounter, clinicalDocument);
        return encounter;
    }

    private void setType(Encounter encounter, Coding interaction) {
        if (REFERRAL.getCode().equals(interaction.getCode())) {
            encounter.addType(new CodeableConcept().setText("111 Encounter Referral"));
        } else if (DISCHARGE_DETAILS.getCode().equals(interaction.getCode())) {
            encounter.addType(new CodeableConcept().setText("111 Encounter Copy for Information"));
        }
    }

    private void setIdentifiers(Encounter encounter, POCDMT000002UK01ClinicalDocument1 clinicalDocument) {
        stream(clinicalDocument.getComponentOf().getEncompassingEncounter().getIdArray())
            .map(it -> new Identifier()
                .setSystem(it.getRoot())
                .setValue(it.getExtension()))
            .forEach(encounter::addIdentifier);
    }

    private List<EncounterLocationComponent> getLocationComponents(POCDMT000002UK01ClinicalDocument1 clinicalDocument1) {
        List<EncounterLocationComponent> locations = new ArrayList<>();
        if (clinicalDocument1.sizeOfRecordTargetArray() > 0) {
            locations = stream(clinicalDocument1.getRecordTargetArray())
                .filter(recordTarget -> recordTarget.getPatientRole().getProviderOrganization() != null)
                .map(recordTarget -> recordTarget.getPatientRole().getProviderOrganization())
                .map(locationMapper::mapOrganizationToLocationComponent)
                .collect(Collectors.toList());
        }

        EncounterLocationComponent healthcareFacility = locationMapper.mapHealthcareFacilityToLocationComponent(clinicalDocument1);
        if (healthcareFacility != null) {
            locations.add(healthcareFacility);
        }

        return locations;
    }

    private Period getPeriod(POCDMT000002UK01ClinicalDocument1 clinicalDocument) {
        TS effectiveTime = clinicalDocument.getEffectiveTime();

        return periodMapper.mapPeriod(effectiveTime);
    }

    private void setServiceProvider(Encounter encounter, POCDMT000002UK01ClinicalDocument1 clinicalDocument1) {
        Organization serviceProviderOrganization = serviceProviderMapper.mapServiceProvider(clinicalDocument1.getCustodian());
        Reference serviceProvider = resourceUtil.createReference(serviceProviderOrganization);
        encounter.setServiceProvider(serviceProvider);
        encounter.setServiceProviderTarget(serviceProviderOrganization);
    }

    private void setSubject(Encounter encounter, POCDMT000002UK01ClinicalDocument1 clinicalDocument1) {
        Optional<Patient> patient = getPatient(clinicalDocument1);
        if (patient.isPresent()) {
            if (clinicalDocument1.sizeOfRecordTargetArray() == 1) {
                encounter.setSubject(resourceUtil.createReference(patient.get()));
                encounter.setSubjectTarget(patient.get());
            } else if (clinicalDocument1.sizeOfRecordTargetArray() > 1) {
                Group group = groupMapper.mapGroup(clinicalDocument1.getRecordTargetArray());
                encounter.setSubject(resourceUtil.createReference(group));
                encounter.setSubjectTarget(group);
            }
        }
    }

    private List<EncounterParticipantComponent> getEncounterParticipantComponents(POCDMT000002UK01ClinicalDocument1 clinicalDocument,
        List<PractitionerRole> authorPractitionerRoles, Optional<PractitionerRole> responsibleParty, Encounter encounter) {
        List<EncounterParticipantComponent> encounterParticipantComponents = stream(clinicalDocument
            .getParticipantArray())
            .filter(it -> !PARTCIPANT_TYPE_CODE_REFT.equals(it.getTypeCode()))
            .map(participantMapper::mapEncounterParticipant)
            .collect(Collectors.toList());
        if (authorPractitionerRoles.size() > 0) {
            authorPractitionerRoles.stream()
                .map(it -> buildParticipantComponent(it, AUTHOR_PARTICIPANT_CODE, AUTHOR_PARTICIPANT_DISPLAY))
                .forEach(encounterParticipantComponents::add);
        }
        if (clinicalDocument.sizeOfInformantArray() > 0) {
            stream(clinicalDocument.getInformantArray())
                .map(informantMapper::mapInformantIntoParticipantComponent)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(encounterParticipantComponents::add);

            for (POCDMT000002UK01Informant12 informant : clinicalDocument.getInformantArray()) {
                EncounterParticipantComponent encounterParticipantComponent =
                    participantMapper.mapEncounterRelatedPerson(informant, encounter);
                encounterParticipantComponents.add(encounterParticipantComponent);
            }
        }
        if (clinicalDocument.isSetDataEnterer()) {
            encounterParticipantComponents.add(dataEntererMapper
                .mapDataEntererIntoParticipantComponent(clinicalDocument.getDataEnterer()));
        }

        responsibleParty.ifPresent(it ->
            encounterParticipantComponents.add(buildParticipantComponent(it, RESPONSIBLE_PARTY_PARTICIPANT_CODE,
                RESPONSIBLE_PARTY_PARTICIPANT_DISPLAY))
        );

        return encounterParticipantComponents;
    }

    private EncounterParticipantComponent buildParticipantComponent(PractitionerRole practitionerRole, String code, String display) {
        return new EncounterParticipantComponent()
            .addType(new CodeableConcept()
                .addCoding(new Coding()
                    .setCode(code)
                    .setSystem(PARTICIPANT_SYSTEM)
                    .setDisplay(display)))
            .setIndividual(practitionerRole.getPractitioner())
            .setIndividualTarget(practitionerRole.getPractitionerTarget());
    }

    private void setAppointment(Encounter encounter, POCDMT000002UK01ClinicalDocument1 clinicalDocument) {
        Reference patient = encounter.getSubject();
        appointmentService.retrieveAppointment(patient, clinicalDocument)
            .ifPresent(it -> encounter.setAppointment(resourceUtil.createReference(it)));
    }

    private void setEncounterReasonAndType(Encounter encounter, POCDMT000002UK01ClinicalDocument1 clinicalDocument) {
        if (clinicalDocument.getComponent().isSetStructuredBody()) {
            for (POCDMT000002UK01Component3 component3 : clinicalDocument.getComponent().getStructuredBody().getComponentArray()) {
                POCDMT000002UK01Section section = component3.getSection();
                for (POCDMT000002UK01Entry entry : section.getEntryArray()) {
                    if (entry.isSetEncounter()) {
                        addEncounterText(entry.getEncounter(), encounter);
                    }
                }
            }
        }
    }

    private Optional<Patient> getPatient(POCDMT000002UK01ClinicalDocument1 clinicalDocument1) {
        Patient patient = new Patient();
        if (clinicalDocument1.sizeOfRecordTargetArray() > 0) {
            POCDMT000002UK01PatientRole patientRole = clinicalDocument1.getRecordTargetArray(0).getPatientRole();
            patient = patientMapper.mapPatient(patientRole);
        }
        return Optional.of(patient);
    }

    private void addEncounterText(POCDMT000002UK01Encounter encounterITK, Encounter encounter) {
        if (encounterITK.isSetText()) {
            String divString = nodeUtil.getNodeValueString(encounterITK.getText());
            Narrative narrative = new Narrative();
            narrative.setStatus(GENERATED);
            narrative.setDivAsString(Arrays.asList(DIV_START, divString, DIV_END)
                .stream().collect(Collectors.joining()));
            encounter.setText(narrative);
        }
    }
}
