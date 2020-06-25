package uk.nhs.adaptors.oneoneone.cda.report.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hl7.fhir.dstu3.model.Encounter.EncounterStatus.FINISHED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.hl7.fhir.dstu3.model.Appointment;
import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.EpisodeOfCare;
import org.hl7.fhir.dstu3.model.Organization;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Period;
import org.hl7.fhir.dstu3.model.Reference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import uk.nhs.adaptors.oneoneone.cda.report.service.AppointmentService;
import uk.nhs.connect.iucds.cda.ucr.POCDMT000002UK01Author;
import uk.nhs.connect.iucds.cda.ucr.POCDMT000002UK01ClinicalDocument1;
import uk.nhs.connect.iucds.cda.ucr.POCDMT000002UK01DataEnterer;
import uk.nhs.connect.iucds.cda.ucr.POCDMT000002UK01Informant12;
import uk.nhs.connect.iucds.cda.ucr.POCDMT000002UK01Organization;
import uk.nhs.connect.iucds.cda.ucr.POCDMT000002UK01Participant1;
import uk.nhs.connect.iucds.cda.ucr.POCDMT000002UK01PatientRole;
import uk.nhs.connect.iucds.cda.ucr.POCDMT000002UK01RecordTarget;
import uk.nhs.connect.iucds.cda.ucr.TS;

@RunWith(MockitoJUnitRunner.class)
public class EncounterMapperTest {

    @Mock
    private ParticipantMapper participantMapper;

    @Mock
    private PeriodMapper periodMapper;

    @Mock
    private AuthorMapper authorMapper;

    @Mock
    private InformantMapper informantMapper;

    @Mock
    private DataEntererMapper dataEntererMapper;

    @Mock
    private ServiceProviderMapper serviceProviderMapper;

    @Mock
    private LocationMapper locationMapper;

    @Mock
    private AppointmentService appointmentService;

    @InjectMocks
    private EncounterMapper encounterMapper;

    @Mock
    private Period period;

    @Mock
    private Encounter.EncounterParticipantComponent encounterParticipantComponent;

    @Mock
    private Organization serviceProvider;

    @Mock
    private Appointment appointment;

    @Mock
    private Patient patient;

    @Mock
    private EpisodeOfCare episodeOfCare;

    @Mock
    private Encounter.EncounterLocationComponent locationComponent;

    @Mock
    private PatientMapper patientMapper;

    @Mock
    private EpisodeOfCareMapper episodeOfCareMapper;

    @Mock
    private POCDMT000002UK01ClinicalDocument1 clinicalDocument;

    @Before
    public void setUp() {
        POCDMT000002UK01RecordTarget recordTarget = mock(POCDMT000002UK01RecordTarget.class);
        POCDMT000002UK01PatientRole patientRole = mock(POCDMT000002UK01PatientRole.class);
        POCDMT000002UK01Organization organization = mock(POCDMT000002UK01Organization.class);

        when(clinicalDocument.getRecordTargetArray()).thenReturn(new POCDMT000002UK01RecordTarget[] { recordTarget });
        when(clinicalDocument.getRecordTargetArray(0)).thenReturn(recordTarget);
        when(clinicalDocument.sizeOfRecordTargetArray()).thenReturn(1);
        when(recordTarget.getPatientRole()).thenReturn(patientRole);
        when(patientRole.getProviderOrganization()).thenReturn(organization);

        mockLocation(clinicalDocument);
        mockPeriod(clinicalDocument);
        mockServiceProvider();
        mockAppointment();
        mockSubject(clinicalDocument);
        mockEpisodeOfCare();
    }

    @Test
    public void mapEncounterTest() {
        mockParticipant(clinicalDocument);

        Encounter encounter = encounterMapper.mapEncounter(clinicalDocument);
        verifyEncounter(encounter);
    }

    @Test
    public void mapEncounterWhenAuthorInformantAndDataEntererArePresent() {
        mockParticipantWithAuthorInformantAndDataEnterer(clinicalDocument);

        Encounter encounter = encounterMapper.mapEncounter(clinicalDocument);
        verifyEncounter(encounter);

        assertThat(encounter.getParticipant().size()).isEqualTo(4);
        for (Encounter.EncounterParticipantComponent component : encounter.getParticipant()) {
            assertThat(component).isEqualTo(encounterParticipantComponent);
        }
    }

    private void verifyEncounter(Encounter encounter) {
        assertThat(encounter.getIdElement().getValue()).startsWith("urn:uuid:");
        assertThat(encounter.getStatus()).isEqualTo(FINISHED);
        assertThat(encounter.getPeriod()).isEqualTo(period);
        assertThat(encounter.getParticipantFirstRep()).isEqualTo(encounterParticipantComponent);
        assertThat(encounter.getAppointmentTarget()).isEqualTo(appointment);
        assertThat(encounter.getServiceProviderTarget()).isEqualTo(serviceProvider);
        assertThat(encounter.getLocationFirstRep()).isEqualTo(locationComponent);
        assertThat(encounter.getSubjectTarget()).isEqualTo(patient);
        assertThat(encounter.getEpisodeOfCareFirstRep().getResource()).isEqualTo(episodeOfCare);
    }

    private void mockParticipant(POCDMT000002UK01ClinicalDocument1 clinicalDocument) {
        POCDMT000002UK01Participant1 participant = mock(POCDMT000002UK01Participant1.class);

        when(clinicalDocument.getParticipantArray()).thenReturn(new POCDMT000002UK01Participant1[] { participant });
        when(participantMapper.mapEncounterParticipant(any())).thenReturn(encounterParticipantComponent);
    }

    private void mockParticipantWithAuthorInformantAndDataEnterer(POCDMT000002UK01ClinicalDocument1 clinicalDocument) {
        mockParticipant(clinicalDocument);

        POCDMT000002UK01Author author = mock(POCDMT000002UK01Author.class);
        POCDMT000002UK01Informant12 informant = mock(POCDMT000002UK01Informant12.class);
        POCDMT000002UK01DataEnterer dataEnterer = mock(POCDMT000002UK01DataEnterer.class);

        when(clinicalDocument.sizeOfAuthorArray()).thenReturn(1);
        when(clinicalDocument.getAuthorArray()).thenReturn(new POCDMT000002UK01Author[] { author });
        when(clinicalDocument.sizeOfInformantArray()).thenReturn(1);
        when(clinicalDocument.getInformantArray()).thenReturn(new POCDMT000002UK01Informant12[] { informant });
        when(clinicalDocument.isSetDataEnterer()).thenReturn(true);
        when(clinicalDocument.getDataEnterer()).thenReturn(dataEnterer);

        when(authorMapper.mapAuthorIntoParticipantComponent(any())).thenReturn(encounterParticipantComponent);
        when(informantMapper.mapInformantIntoParticipantComponent(any())).thenReturn(Optional.of(encounterParticipantComponent));
        when(dataEntererMapper.mapDataEntererIntoParticipantComponent(any())).thenReturn(encounterParticipantComponent);
    }

    private void mockLocation(POCDMT000002UK01ClinicalDocument1 clinicalDocument) {
        when(locationMapper.mapOrganizationToLocationComponent(any())).thenReturn(locationComponent);
    }

    private void mockPeriod(POCDMT000002UK01ClinicalDocument1 clinicalDocument) {
        TS effectiveTime = mock(TS.class);

        when(clinicalDocument.getEffectiveTime()).thenReturn(effectiveTime);
        when(periodMapper.mapPeriod(ArgumentMatchers.isA(TS.class))).thenReturn(period);
    }

    private void mockServiceProvider() {
        when(serviceProviderMapper.mapServiceProvider(any())).thenReturn(serviceProvider);
    }

    private void mockAppointment() {
        when(appointmentService.retrieveAppointment(any(), any(), any())).thenReturn(Optional.of(appointment));
    }

    private void mockSubject(POCDMT000002UK01ClinicalDocument1 clinicalDocument) {
        when(patientMapper.mapPatient(any())).thenReturn(patient);
    }

    private void mockEpisodeOfCare() {
        when(episodeOfCareMapper.mapEpisodeOfCare(any(POCDMT000002UK01ClinicalDocument1.class), any(Reference.class)))
            .thenReturn(Optional.of(episodeOfCare));
    }
}
