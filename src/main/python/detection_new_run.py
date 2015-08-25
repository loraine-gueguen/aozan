# -*- coding: utf-8 -*-

'''
Created on 6 avril 2015

With include NextSeq management, replace old first_base_report.py
 
@author: sperrin
'''

import common, aozan, hiseq_run, detection_end_run
import estimate_space_needed
import os
from java.io import File
from fr.ens.transcriptome.aozan.illumina import RunInfo

from fr.ens.transcriptome.aozan.Settings import AOZAN_VAR_PATH_KEY
from fr.ens.transcriptome.aozan.Settings import HISEQ_DATA_PATH_KEY
from fr.ens.transcriptome.aozan.Settings import FIRST_BASE_REPORT_STEP_KEY
from fr.ens.transcriptome.aozan.Settings import HISEQ_STEP_KEY

DONE_FILE = 'detection_new_run.done'


def load_processed_run_ids(conf):
    """Load the list of the processed run ids.

    Arguments:
        conf: configuration dictionary
    """

    return common.load_processed_run_ids(conf[AOZAN_VAR_PATH_KEY] + '/' + DONE_FILE)

def add_run_id_to_processed_run_ids(run_id, conf):
    """Add a processed run id to the list of the run ids.

    Arguments:
        run id: The run id
        conf: configuration dictionary
    """

    common.add_run_id_to_processed_run_ids(run_id, conf[AOZAN_VAR_PATH_KEY] + '/' + DONE_FILE, conf)


def get_available_run_ids(conf):
    """Get the list of the available runs.

    Arguments:
        conf: configuration dictionary
    """

    result = set()

    for hiseq_data_path in hiseq_run.get_hiseq_data_paths(conf):

        files = os.listdir(hiseq_data_path)
        for f in files:
            if os.path.isdir(hiseq_data_path + '/' + f) and \
                hiseq_run.check_run_id(f, conf) and \
                not detection_end_run.check_end_run(f, conf) and \
                os.path.exists(hiseq_data_path + '/' + f + '/RunInfo.xml'):
                
                # os.path.exists(hiseq_data_path + '/' + f + '/First_Base_Report.htm'):

                result.add(f)

    return result

def discover_new_run(conf):
    """Discover new runs.

    Arguments:
        conf: configuration object  
    """

    #
    # Discover new run
    #

    run_already_discovered = load_processed_run_ids(conf)

    if common.is_conf_value_equals_true(FIRST_BASE_REPORT_STEP_KEY, conf):
        for run_id in (get_available_run_ids(conf) - run_already_discovered):
            aozan.welcome(conf)
            common.log('INFO', 'First base report ' + run_id + ' on sequencer ' + common.get_sequencer_type(run_id, conf), conf)
            send_report(run_id, conf)
            add_run_id_to_processed_run_ids(run_id, conf)
            run_already_discovered.add(run_id)

            # Verify space needed during the first base report
            estimate_space_needed.estimate(run_id, conf)
            

    #
    # Discover hiseq run done
    #

    return detection_end_run.discovery_run(conf)


def send_report(run_id, conf):
    """Send a mail with the first base report.
    
    Arguments:
        run_id: the run id
        conf: configuration dictionary
    """

    #
    # Retrieve features the current run in RunInfos.xml file
    #

    run_info_path = hiseq_run.get_runinfos_file(run_id, conf)
    run_info = RunInfo()
    run_info.parse(File(run_info_path))


    # TODO ?? add check sample-sheet if demux step enable
    # add warning in report if useful

    reads = run_info.getReads()
    error_cycles_per_reads_not_indexes_count = 0
    reads_indexed_count = 0
    reads_not_indexed_count = 0
    cycles_count = 0
    cycles_per_reads_not_indexed = 0

    for read in reads:
        cycles_count += read.getNumberCycles()
        if read.isIndexedRead():
            reads_indexed_count += 1
        else:
            reads_not_indexed_count += 1
            if (cycles_per_reads_not_indexed == 0):
                cycles_per_reads_not_indexed = read.getNumberCycles()

            # Check same cycles count for each reads not indexed
            error_cycles_per_reads_not_indexes_count = cycles_per_reads_not_indexed != read.getNumberCycles()


    # Identification type run according to data in RunInfos.xml : SR or PE
    if reads_not_indexed_count == 1:
        type_run_estimated = "SR-" + str(cycles_per_reads_not_indexed - 1) + " with " + str(reads_indexed_count) + " index(es)"
    elif reads_not_indexed_count == 2:
        type_run_estimated = "PE-" + str(cycles_per_reads_not_indexed - 1) + " with " + str(reads_indexed_count) + " index(es)"
    else :
        type_run_estimated = "Undetermined run type (" + str(reads_not_indexed_count) + " reads with " + str(reads_indexed_count) + " index(es))"

    description_run = "Informations about this run :\n"
    description_run += "\t- " + str(run_info.getFlowCellLaneCount()) + " lanes with " + str(run_info.alignToPhix.size()) + " aligned to Phix.\n"
    description_run += "\t- " + str(reads_not_indexed_count) + " read(s) and " + str(reads_indexed_count) + " index(es).\n"

    if error_cycles_per_reads_not_indexes_count or cycles_per_reads_not_indexed == 0:
        description_run += "\t- ERROR : cycles count per reads different between reads (" + str(cycles_count) + " total cycles).\n"
    else:
        description_run += "\t- " + str(cycles_per_reads_not_indexed) + " cycles per reads (" + str(cycles_count) + " total cycles).\n"

    description_run += "\t- " + "estimated run type : " + type_run_estimated + ".\n"

    sequencer_type = common.get_sequencer_type(run_id, conf)

    if sequencer_type == 'hiseq':
        # With HiSeq send the first base report file
        attachment_file = str(hiseq_run.find_hiseq_run_path(run_id, conf)) + '/' + run_id + '/First_Base_Report.htm'
        message = 'You will find attached to this message the first base report on sequencer HiSeq for the run ' + run_id + '.\n\n' + description_run
        common.send_msg_with_attachment('[Aozan] First base report for HiSeq run ' + type_run_estimated + '  ' + run_id , message, attachment_file, conf)

    else:
        # With other no attachment file
        message = 'You will find below features on new run on NextSeq ' + run_id + '.\n\n' + description_run
        common.send_msg('[Aozan] Detection new run on sequencer NextSeq ' + type_run_estimated + '  ' + run_id , message, False, conf)
