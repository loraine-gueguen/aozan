# -*- coding: utf-8 -*-

'''
Created on 25 oct. 2011

@author: Laurent Jourdren
'''
import os.path
import common, hiseq_run, time
from java.io import IOException
from java.lang import Runtime
from java.util import HashMap
from fr.ens.transcriptome.eoulsan import EoulsanException
from fr.ens.transcriptome.aozan.io import CasavaDesignXLSReader
from fr.ens.transcriptome.eoulsan.illumina import CasavaDesignUtil
from fr.ens.transcriptome.eoulsan.illumina.io import CasavaDesignCSVWriter

def load_processed_run_ids(conf):
    """Load the list of the processed run ids.

    Arguments:
        conf: configuration dictionary
    """

    return common.load_processed_run_ids(conf['aozan.var.path'] + '/demux.done')

def add_run_id_to_processed_run_ids(run_id, conf):
    """Add a processed run id to the list of the run ids.

    Arguments:
        run id: The run id
        conf: configuration dictionary
    """

    common.add_run_id_to_processed_run_ids(conf['aozan.var.path'] + '/demux.done')


def error(short_message, message, conf):
    """Error handling.

    Arguments:
        short_message: short description of the message
        message: message
        conf: configuration dictionary
    """

    common.error('[Aozan] demultiplexer: ' + short_message, message, conf['aozan.var.path'] + '/demux.lasterr', conf)


def load_index_sequences(conf):
    """Load the map of the index sequences.

    Arguments:
        index_shortcut_path: the path to the index sequences
    """

    result = HashMap()

    if conf['index.sequences'] == '' or not os.path.exists(conf['index.sequences']):
            return result
            
    

    f = open(conf['index.sequences'], 'r')

    for l in f:
        l = l[:-1]
        if len(l) == 0:
            continue
        fields = l.split('=')
        if len(fields)==2:
            result[fields[0].strip().lower()]=fields[1].strip().upper()

    f.close()

    return result
   

def demux(run_id, conf):
    """Add a processed run id to the list of the run ids.

    Arguments:
        run id: The run id
        conf: configuration dictionary
    """

    start_time = time.time()
    
    reports_data_base_path = conf['reports.data.path']
    reports_data_path = reports_data_base_path + '/' + run_id
    
    
    run_number = hiseq_run.get_run_number(run_id)
    flow_cell_id = hiseq_run.get_flow_cell(run_id)
    design_xls_path = conf['casava.designs.path'] + '/design-%04d.xls' % run_number
    design_csv_path = conf['tmp.path'] + '/design-%04d.csv' % run_number
    fastq_output_dir = conf['fastq.data.path'] + '/fastq_%04d' % hiseq_run.get_run_number(run_id)
    
    basecall_stats_prefix = 'basecall_stats_'
    basecall_stats_file =  basecall_stats_prefix + run_id + '.tar.bz2'
    

    common.log("DEBUG", "Flowcell id: " + flow_cell_id, conf)
    
    # Check if input data exists
    if not os.path.exists(conf['work.data.path']):
        error("Input data directory does not exists", "Input data directory does not exists: " + conf['work.data.path'], conf)
        return False

    # Check if casava designs path exists
    if not os.path.exists(conf['casava.designs.path']):
        error("Casava designs directory does not exists", "Casava designs does not exists: " + conf['casava.designs.path'], conf)
        return False
    
    # Check if temporary directory exists
    if not os.path.exists(conf['tmp.path']):
        error("Temporary directory does not exists", "Temporary directory does not exists: " + conf['tmp.path'], conf)
        return False
    
    # Check if reports_data_path exists
    if not os.path.exists(reports_data_base_path):
        error("Report directory does not exists", "Report directory does not exists: " + reports_data_base_path, conf)
        return False
    
    # Create if not exists archive directory for the run
    if not os.path.exists(reports_data_base_path + '/' + run_id):
        os.mkdir(reports_data_base_path + '/' + run_id)    


    # Check if the xls design exists
    if not os.path.exists(design_xls_path):
        error("no casava design found", "No casava design found for " + run_id + " run.\n" + \
              'You must provide a design-%04d.xls file' % run_number + ' in ' + conf['casava.designs.path'] + \
              ' directory to demultiplex and create fastq files for this run.\n', conf)
        return False

    # Check if the output directory already exists
    if os.path.exists(fastq_output_dir):
        error("fastq output directory already exists for run " + run_id,
              'The fastq output directory already exists for run ' + run_id + ': ' + fastq_output_dir, conf)
        return False

    # Compute disk usage and disk free to check if enough disk space is available 
    input_path_du = common.du(conf['work.data.path'] + '/' + run_id)
    output_df = common.df(conf['fastq.data.path'])
    du_factor = float(conf['sync.space.factor'])
    space_needed = input_path_du * du_factor

    common.log("DEBUG", "Demux step: input disk usage: " + str(input_path_du), conf)
    common.log("DEBUG", "Demux step: output disk free: " + str(output_df), conf)
    common.log("DEBUG", "Demux step: space needed: " + str(space_needed), conf)

    # Check if free space is available 
    if output_df < space_needed:
        error("Not enough disk space to perform demultiplexing for run " + run_id, "Not enough disk space to perform synchronization for run " + run_id +
              '.\n%.2f Gb' % (space_needed / 1024 / 1024 / 1024) + ' is needed (factor x' + str(du_factor) + ') on ' + fastq_output_dir + '.', conf)
        return False

    # Convert design in XLS format to CSV format
    try:
        # Load XLS design file
        design = CasavaDesignXLSReader(design_xls_path).read()

        # Replace index sequence shortcuts by sequences
        CasavaDesignUtil.replaceIndexShortcutsBySequences(design, load_index_sequences(conf))
        
        # Check values of design file
        CasavaDesignUtil.checkCasavaDesign(design, flow_cell_id)
        
        # Write CSV design file
        CasavaDesignCSVWriter(design_csv_path).writer(design)
        
    except IOException, exp:
        error("error while converting design-%04d" % run_number + ".xls to CSV format", exp.getMessage(), conf)
        return False        
    except EoulsanException, exp:
        error("error while converting design-%04d" % run_number + ".xls to CSV format", exp.getMessage(), conf)
        return False

    # Create casava makefile
    cmd = conf['casava.path'] + '/bin/configureBclToFastq.pl ' + \
          '--fastq-cluster-count ' + conf['casava.fastq.cluster.count'] + ' ' + \
          '--compression ' + conf['casava.compression'] + ' ' + \
          '--gz-level ' + conf['casava.compression.level'] + ' ' \
          '--mismatches ' + conf['casava.mismatches'] + ' ' + \
          '--input-dir ' + conf['work.data.path'] + '/' + run_id + '/Data/Intensities/BaseCalls ' + \
          '--sample-sheet ' + design_csv_path + ' ' + \
          '--output-dir ' + fastq_output_dir
    common.log("DEBUG", "exec: " + cmd, conf)
    if os.system(cmd) != 0:
        error("error while creating Casava makefile for run " + run_id, 'Error while creating Casava makefile.\nCommand line:\n' + cmd, conf)
        return False

    # Get the number of cpu
    cpu_count = Runtime.getRuntime().availableProcessors()

    # Launch casava
    cmd = "cd " + fastq_output_dir + " && make -j " + str(cpu_count)
    common.log("DEBUG", "exec: " + cmd, conf)
    if os.system(cmd) != 0:
        error("error while running Casava for run " + run_id, 'Error while creating Casava makefile.\nCommand line:\n' + cmd, conf)
        return False

    # Copy design to output directory
    cmd = "cp -p " + design_csv_path + ' ' + fastq_output_dir
    common.log("DEBUG", "exec: " + cmd, conf)
    if os.system(cmd) != 0:
        error("error while copying design file to the fastq directory for run " + run_id, 'Error while copying design file to fastq directory.\nCommand line:\n' + cmd, conf)
        return False

    # Archive basecall stats
    cmd = 'cd ' + fastq_output_dir + ' &&  mv Basecall_Stats_' + flow_cell_id + ' ' + basecall_stats_prefix + run_id + ' && ' + \
        'tar cjf ' + reports_data_path + '/' + basecall_stats_file + ' ' + basecall_stats_prefix + run_id + ' && ' + \
        'cp -rp ' + basecall_stats_prefix + run_id + ' ' + reports_data_path + ' && ' + \
        'mv ' + basecall_stats_prefix + run_id + ' Basecall_Stats_' + flow_cell_id
    common.log("DEBUG", "exec: " + cmd, conf)
    if os.system(cmd) != 0:
        error("error while saving the basecall stats file for " + run_id, 'Error while saving the basecall stats files.\nCommand line:\n' + cmd, conf)
        return False

    # The output directory must be read only
    cmd = 'chmod -R ugo-w ' + fastq_output_dir + '/Project_*'
    common.log("DEBUG", "exec: " + cmd, conf)
    if os.system(cmd) != 0:
        error("error while setting read only the output fastq directory for run " + run_id, 'Error while setting read only the output fastq directory.\nCommand line:\n' + cmd, conf)
        return False



    # Add design to the archive of designs
    cmd = 'zip ' + conf['casava.designs.path'] + '/designs.zip ' + design_csv_path + ' ' + design_xls_path
    common.log("DEBUG", "exec: " + cmd, conf)
    if os.system(cmd) != 0:
        error("error while archiving the design file for " + run_id, 'Error while archiving the design file for.\nCommand line:\n' + cmd, conf)
        return False

    # Remove temporary design file
    os.remove(design_csv_path)

    duration = time.time() - start_time
    df = common.df(fastq_output_dir) / (1024 * 1024 * 1024)
    du = common.du(fastq_output_dir) / (1024 * 1024)


    msg = 'End of demultiplexing for run ' + run_id + '.' + \
        '\nJob finished at ' + common.time_to_human_readable(time.time()) + \
        ' with no error in ' + common.duration_to_human_readable(duration) + '.\n\n' + \
        'Fastq files for this run ' + \
        'can be found in the following directory:\n  ' + fastq_output_dir

    # Add path to report if reports.url exists
    if conf['reports.url'] != None and conf['reports.url'] != '':
        msg += '\n\nRun reports can be found at following location:\n  ' +  conf['reports.url'] + '/' + run_id

    msg += '\n\nFor this task %.2f GB has been used and %.2f GB still free.' % (du, df)

    common.send_msg('[Aozan] End of demultiplexing for run ' + run_id, msg, conf)
    return True
